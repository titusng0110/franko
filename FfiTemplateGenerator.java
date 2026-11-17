import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FfiTemplateGenerator {

    private static final List<String> TARGETS = List.of(
            "linux-x86_64",
            "linux-arm64",
            "windows-x86_64",
            "windows-arm64",
            "macos-x86_64",
            "macos-arm64"
    );

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (RuntimeException e) {
            System.err.println("frbridge: " + e.getMessage());
            System.exit(1);
        }
    }

    private static int run(String[] args) {
        if (args.length < 2) {
            System.err.println(
                    "usage: FfiTemplateGenerator <module.fr> <import-dir>"
            );
            return 2;
        }

        Path module = Path.of(args[0]).toAbsolutePath().normalize();
        Path importDir = Path.of(args[1]).toAbsolutePath().normalize();

        if (!Files.isRegularFile(module)) {
            System.err.println("frbridge: module not found: " + module);
            return 1;
        }

        ModuleRegistry modules = new ModuleRegistry();
        Map<String, FunctionEntry> merged = new LinkedHashMap<>();
        List<Diagnostic> lastDiagnostics = List.of();
        boolean anySuccess = false;

        for (String spec : TARGETS) {
            TargetInfo target = TargetInfo.parse(spec);
            Analysis analysis = analyzeTarget(module, importDir, modules, target);

            if (!analysis.success) {
                lastDiagnostics = analysis.diagnostics;
                continue;
            }

            anySuccess = true;

            for (FunctionEntry entry : analysis.functions) {
                FunctionEntry existing = merged.get(entry.symbol.qualifiedName());

                if (existing == null) {
                    merged.put(entry.symbol.qualifiedName(), entry);
                } else if (!existing.sameSignature(entry)) {
                    throw new IllegalArgumentException(
                            "function '" + entry.symbol.qualifiedName()
                                    + "' has different signatures across targets;"
                                    + " target-dependent FFI signatures are not"
                                    + " supported by this template generator"
                    );
                }
            }
        }

        if (!anySuccess) {
            printDiagnostics(lastDiagnostics);
            System.err.println(
                    "frbridge: module could not be analyzed for any target"
            );
            return 1;
        }

        List<FunctionEntry> entries = new ArrayList<>(merged.values());

        if (entries.isEmpty()) {
            System.err.println(
                    "frbridge: no bridgeable functions found in "
                            + SourcePaths.display(module)
            );
            return 1;
        }

        ModuleDescriptor descriptor = modules.loadFor(module);
        List<FunctionEntry> selected;

        if (descriptor != null && descriptor.hasBridgedEntities()) {
            selected = selectDescriptorFunctions(entries, descriptor);
        } else {
            selected = selectModuleFunctions(entries, module);
        }

        if (selected.isEmpty()) {
            System.err.println(
                    "frbridge: no bridgeable functions found in "
                            + SourcePaths.display(module)
            );
            return 1;
        }

        System.out.print(renderBridge(selected));
        return 0;
    }

    private static Analysis analyzeTarget(
            Path module,
            Path importDir,
            ModuleRegistry modules,
            TargetInfo target
    ) {
        Frontend frontend = new Frontend(
                new JsonArtifacts(module, false),
                modules,
                target
        );

        Result<AstDocument> rawResult = frontend.parseFile(module);

        if (rawResult.hasErrors() || rawResult.value == null) {
            return Analysis.failed(rawResult.diagnostics);
        }

        Result<AstDocument> desugaredResult = frontend.desugar(rawResult.value);

        if (desugaredResult.hasErrors() || desugaredResult.value == null) {
            return Analysis.failed(desugaredResult.diagnostics);
        }

        ImportResolver importResolver = new ImportResolver(importDir, frontend);
        ImportResult importResult = importResolver.resolve(
                desugaredResult.value,
                module
        );

        if (importResult.hasErrors()) {
            return Analysis.failed(importResult.diagnostics);
        }

        AstDocument document = importResult.document;

        if (document == null || document.root == null) {
            return Analysis.failed(List.of());
        }

        if (!hasGlobalMain(document.root)) {
            List<AstNode> items = new ArrayList<>(document.root.items);
            items.add(syntheticMain());
            document = new AstDocument(
                    document.phase,
                    document.sources,
                    AstNode.program(document.root.span, items)
            );
        }

        PassResult semanticResult = new SemanticAnalyzer(target).analyze(document);

        if (semanticResult.hasErrors() || semanticResult.document == null) {
            return Analysis.failed(semanticResult.diagnostics);
        }

        return Analysis.succeeded(
                collectFunctions(semanticResult.document.root)
        );
    }

    private static void printDiagnostics(List<Diagnostic> diagnostics) {
        for (Diagnostic diagnostic : diagnostics) {
            System.err.println(diagnostic.format());
        }
    }

    private static boolean hasGlobalMain(AstNode root) {
        if (root == null || root.kind != AstKind.PROGRAM) {
            return false;
        }

        for (AstNode item : root.items) {
            if (item.kind == AstKind.FUNCTION_DECL
                    && "main".equals(item.name)) {
                return true;
            }
        }

        return false;
    }

    private static AstNode syntheticMain() {
        AstNode int32 = AstNode.primitiveType(SourceSpan.NONE, PrimitiveKind.INT32);
        AstNode zero = AstNode.intLiteral(SourceSpan.NONE, "0");
        AstNode body = AstNode.block(
                SourceSpan.NONE,
                List.of(AstNode.returnStmt(SourceSpan.NONE, zero))
        );

        return AstNode.functionDecl(
                SourceSpan.NONE,
                "main",
                List.of(),
                int32,
                body,
                InlineMode.DEFAULT
        );
    }

    private static List<FunctionEntry> collectFunctions(
            SastNode root
    ) {
        List<FunctionEntry> out = new ArrayList<>();
        collectFunctions(root, out);
        return out;
    }

    private static void collectFunctions(
            SastNode node,
            List<FunctionEntry> out
    ) {
        if (node == null) {
            return;
        }

        if (node.kind == SastKind.PROGRAM) {
            for (SastNode item : node.topLevelItems) {
                collectFunctions(item, out);
            }
            return;
        }

        if (node.kind == SastKind.NAMESPACE) {
            for (SastNode item : node.items) {
                collectFunctions(item, out);
            }
            return;
        }

        if (node.kind == SastKind.FUNCTION_DECL && node.symbol != null) {
            out.add(new FunctionEntry(node, node.symbol));
        }
    }

    private static List<FunctionEntry> selectDescriptorFunctions(
            List<FunctionEntry> entries,
            ModuleDescriptor descriptor
    ) {
        Map<String, List<FunctionEntry>> byQualifiedName = new LinkedHashMap<>();

        for (FunctionEntry entry : entries) {
            byQualifiedName.computeIfAbsent(
                    entry.symbol.qualifiedName(),
                    ignored -> new ArrayList<>()
            ).add(entry);
        }

        List<FunctionEntry> selected = new ArrayList<>();

        for (ModuleFunction function : descriptor.functions) {
            String qualifiedName = function.qualifiedName();
            List<FunctionEntry> matches = byQualifiedName.get(qualifiedName);

            if (matches == null || matches.isEmpty()) {
                System.err.println(
                        "frbridge: warning: '" + qualifiedName
                                + "' was not found in any target analysis;"
                                + " no bridge block was generated"
                );
                continue;
            }

            if (matches.size() > 1) {
                throw new IllegalArgumentException(
                        "function '" + qualifiedName
                                + "' is overloaded; FFI templates require"
                                + " a single function per qualified name"
                );
            }

            selected.add(matches.get(0));
        }

        return selected;
    }

    private static List<FunctionEntry> selectModuleFunctions(
            List<FunctionEntry> entries,
            Path module
    ) {
        String source = SourcePaths.display(module);
        Set<String> seen = new LinkedHashSet<>();
        List<FunctionEntry> selected = new ArrayList<>();

        for (FunctionEntry entry : entries) {
            if (!source.equals(entry.node.span.source())) {
                continue;
            }

            if ("main".equals(entry.symbol.name)
                    && entry.symbol.namespace == null) {
                continue;
            }

            String qualifiedName = entry.symbol.qualifiedName();

            if (!seen.add(qualifiedName)) {
                throw new IllegalArgumentException(
                        "function '" + qualifiedName
                                + "' is overloaded; FFI templates require"
                                + " a single function per qualified name"
                );
            }

            selected.add(entry);
        }

        return selected;
    }

    private static String renderBridge(List<FunctionEntry> functions) {
        Cpp14Codegen codegen = new Cpp14Codegen();
        StringBuilder out = new StringBuilder();

        out.append("// @@common\n");
        out.append("#include \"FrankoRuntime.hpp\"\n");
        out.append("// @@end\n");

        for (FunctionEntry entry : functions) {
            Symbol symbol = entry.symbol;
            out.append('\n');
            out.append("// @@").append(symbol.qualifiedName()).append('\n');

            List<String> namespace = SymbolTable.splitNamespacePath(
                    symbol.namespace
            );

            for (String segment : namespace) {
                out.append("namespace NS_").append(segment).append(" {\n");
            }

            String inlinePrefix = symbol.inlineMode == InlineMode.INLINE
                    ? "FRANKO_INLINE "
                    : symbol.inlineMode == InlineMode.NOINLINE
                            ? "FRANKO_NOINLINE "
                            : "";

            out.append(inlinePrefix)
                    .append(codegen.emitType(symbol.returnType()))
                    .append(' ')
                    .append(symbol.emittedLocalName())
                    .append('(');

            for (int i = 0; i < symbol.parameters.size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }

                Symbol parameter = symbol.parameters.get(i);
                out.append(codegen.emitType(parameter.type))
                        .append(' ')
                        .append(codegen.emitSymbolName(parameter));
            }

            out.append(")\n");
            out.append("{\n");
            out.append("    // TODO: implement ")
                    .append(symbol.qualifiedName())
                    .append('\n');

            if (symbol.returnType().kind == SastTypeKind.VOID) {
                out.append("    return;\n");
            } else {
                out.append("    return {};\n");
            }

            out.append("}\n");

            for (int i = namespace.size(); i > 0; i--) {
                out.append("}\n");
            }

            out.append("// @@end\n");
        }

        return out.toString();
    }

    private static final class FunctionEntry {
        final SastNode node;
        final Symbol symbol;

        FunctionEntry(SastNode node, Symbol symbol) {
            this.node = node;
            this.symbol = symbol;
        }

        boolean sameSignature(FunctionEntry other) {
            return symbol.emittedLocalName().equals(
                    other.symbol.emittedLocalName()
            ) && symbol.fullSignatureString().equals(
                    other.symbol.fullSignatureString()
            );
        }
    }

    private static final class Analysis {
        final boolean success;
        final List<FunctionEntry> functions;
        final List<Diagnostic> diagnostics;

        private Analysis(
                boolean success,
                List<FunctionEntry> functions,
                List<Diagnostic> diagnostics
        ) {
            this.success = success;
            this.functions = functions == null ? List.of() : functions;
            this.diagnostics = diagnostics == null ? List.of() : diagnostics;
        }

        static Analysis succeeded(List<FunctionEntry> functions) {
            return new Analysis(true, functions, List.of());
        }

        static Analysis failed(List<Diagnostic> diagnostics) {
            return new Analysis(false, List.of(), diagnostics);
        }
    }
}
