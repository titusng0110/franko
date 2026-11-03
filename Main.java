import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class Main {

    private static final class Abort extends RuntimeException {
        private static final long serialVersionUID = 1L;
        Abort(String message) {
            super(message);
        }
    }

    public static void main(String[] args) throws Exception {
        String sourceFile = null;
        String outputFile = null;
        Path importSearchDir = null;
        String targetSpec = null;
        boolean optimize = false;
        boolean maxOptimize = false;
        boolean emitJson = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-o":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value after -o");
                    }
                    outputFile = args[++i];
                    break;

                case "-p":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value after -p");
                    }
                    importSearchDir = Path.of(args[++i]);
                    break;

                case "--emit-json":
                    emitJson = true;
                    break;

                case "-O":
                    if (maxOptimize) {
                        throw new IllegalArgumentException("-O and -Omax are mutually exclusive");
                    }
                    optimize = true;
                    break;

                case "-Omax":
                    if (optimize) {
                        throw new IllegalArgumentException("-O and -Omax are mutually exclusive");
                    }
                    maxOptimize = true;
                    break;

                case "--target":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value after --target");
                    }
                    targetSpec = args[++i];
                    break;

                default:
                    if (args[i].startsWith("-")) {
                        throw new IllegalArgumentException("Unknown argument: " + args[i]);
                    }

                    if (sourceFile != null) {
                        throw new IllegalArgumentException(
                                "Multiple source files: '" + sourceFile
                                        + "' and '" + args[i] + "'");
                    }

                    sourceFile = args[i];
            }
        }

        if (sourceFile == null) {
            throw new IllegalArgumentException("Missing source file argument");
        }

        if (outputFile == null) {
            throw new IllegalArgumentException(
                    "Missing required -o <output.cpp> argument");
        }

        if (importSearchDir == null) {
            throw new IllegalArgumentException(
                    "Missing required -p <import-dir> argument");
        }

        if (targetSpec == null) {
            throw new IllegalArgumentException(
                    "Missing required --target <os>-<arch> argument");
        }

        Path outPath = Path.of(outputFile);
        JsonArtifacts artifacts = new JsonArtifacts(outPath, emitJson);
        DiagnosticLog log = new DiagnosticLog();
        artifacts.deleteStaleArtifacts();

        try {
            runPipeline(
                    Path.of(sourceFile),
                    outPath,
                    importSearchDir,
                    targetSpec,
                    maxOptimize,
                    artifacts,
                    log
            );
        } catch (Abort abort) {
            throw abort;
        } catch (RuntimeException e) {
            log.add("internal", Diagnostic.error(
                    e.getMessage() == null ? e.getClass().getName() : e.getMessage()
            ));
            artifacts.writeDiagnostics(log);
            throw e;
        }
    }

    private static void runPipeline(
            Path sourceFile,
            Path outPath,
            Path importSearchDir,
            String targetSpec,
            boolean maxOptimize,
            JsonArtifacts artifacts,
            DiagnosticLog log
    ) throws Exception {
        TargetInfo target = TargetInfo.parse(targetSpec);

        ModuleRegistry modules = new ModuleRegistry();
        Frontend frontend = new Frontend(artifacts, modules);
        CompileTimeInjector compileTimeInjector = new CompileTimeInjector(target);

        Result<AstDocument> rawResult = frontend.parseFile(sourceFile);
        log.add("parse", rawResult.diagnostics);

        if (rawResult.value == null) {
            abort(log, artifacts, "Parse failed:", rawResult.diagnostics);
        }

        AstDocument raw = rawResult.value;
        artifacts.writeAst(AstPhase.RAW, raw);

        if (rawResult.hasErrors()) {
            abort(log, artifacts, "Module binding failed:", rawResult.diagnostics);
        }

        Result<AstDocument> desugaredResult = frontend.desugar(raw);
        log.add("desugar", desugaredResult.diagnostics);

        if (desugaredResult.value == null) {
            abort(log, artifacts, "Desugaring failed:", desugaredResult.diagnostics);
        }

        AstDocument desugared = desugaredResult.value;
        artifacts.writeAst(AstPhase.DESUGARED, desugared);

        if (desugaredResult.hasErrors()) {
            abort(log, artifacts, "Desugaring failed:", desugaredResult.diagnostics);
        }

        ImportResolver importResolver = new ImportResolver(
                importSearchDir,
                frontend,
                compileTimeInjector
        );
        ImportResult importResult = importResolver.resolve(desugared, sourceFile);
        log.add("imports", importResult.diagnostics);
        artifacts.writeAst(AstPhase.IMPORT_RESOLVED, importResult.document);

        if (importResult.hasErrors()) {
            abort(log, artifacts, "Import resolution failed:", importResult.diagnostics);
        }

        PassResult semaResult = runPass(
                log,
                artifacts,
                "semantic",
                SastPhase.SEMANTIC,
                null,
                "==== Warnings ====",
                "Semantic analysis failed:",
                () -> new SemanticAnalyzer().analyze(importResult.document)
        );

        PassResult legalityResult = runPass(
                log,
                artifacts,
                "legality",
                SastPhase.LEGALITY,
                "==== Legality Checking ====",
                null,
                "Legality checking failed:",
                () -> new LegalityChecker().check(semaResult.document)
        );

        SastDocument branchPruned = new BranchPruner().prune(legalityResult.document);
        artifacts.writeSast(SastPhase.BRANCH_PRUNED, branchPruned);

        SastDocument inactivePruned =
                new InactiveDeclarationPruner().prune(branchPruned);
        artifacts.writeSast(SastPhase.INACTIVE_PRUNED, inactivePruned);

        PassResult pointerStateResult = runPass(
                log,
                artifacts,
                "null-state",
                SastPhase.NULL_STATE,
                "==== Pointer Null-State Checking ====",
                "==== Pointer Null-State Warnings ====",
                "Pointer null-state checking failed:",
                () -> new PointerNullStateChecker(new DiagnosticBag())
                        .check(inactivePruned)
        );

        PassResult danglingResult = runPass(
                log,
                artifacts,
                "dangling",
                SastPhase.DANGLING,
                "==== Dangling Pointer Checking ====",
                "==== Dangling Pointer Warnings ====",
                "Dangling pointer checking failed:",
                () -> new DanglingPointerChecker(new DiagnosticBag())
                        .check(pointerStateResult.document)
        );

        PassResult dynamicArrayResult = runPass(
                log,
                artifacts,
                "dyn-array",
                SastPhase.DYN_ARRAY,
                "==== Dynamic Array Memory Checking ====",
                "==== Dynamic Array Memory Warnings ====",
                "Dynamic array memory checking failed:",
                () -> new DynamicArrayMemoryChecker(new DiagnosticBag())
                        .check(danglingResult.document)
        );

        PassResult permissionResult = runPass(
                log,
                artifacts,
                "permissions",
                SastPhase.PERMISSIONS,
                "==== Pointer Permission Linting ====",
                "==== Pointer Permission Warnings ====",
                "Pointer permission linting failed:",
                () -> new PointerPermissionChecker(new DiagnosticBag())
                        .check(dynamicArrayResult.document)
        );

        PassResult privateIdentifierResult = runPass(
                log,
                artifacts,
                "private-id",
                SastPhase.PRIVATE_ID,
                "==== Private Identifier Linting ====",
                "==== Private Identifier Warnings ====",
                "Private identifier linting failed:",
                () -> new PrivateIdentifierLinter(new DiagnosticBag())
                        .check(permissionResult.document)
        );

        FfiHandler ffiHandler = new FfiHandler(modules);
        SastDocument checkedDocument = privateIdentifierResult.document;

        if (modules.hasFfiEntities()) {
            PassResult ffiResult = ffiHandler.verify(privateIdentifierResult.document);
            log.add("ffi-verified", ffiResult.diagnostics);
            artifacts.writeSast(SastPhase.FFI_VERIFIED, ffiResult.document);

            if (ffiResult.hasErrors()) {
                abort(log, artifacts, "FFI verification failed:", ffiResult.diagnostics);
            }

            checkedDocument = ffiResult.document;
        }

        checkedDocument = checkedDocument.withPhase(SastPhase.CHECKED);
        artifacts.writeSast(SastPhase.CHECKED, checkedDocument);

        System.out.println("All checks passed.");

        FfiHandler activeFfiHandler = modules.hasFfiEntities()
                ? ffiHandler
                : null;
        Cpp14Codegen codegen = new Cpp14Codegen(activeFfiHandler, maxOptimize);
        String generatedBody = codegen.generate(checkedDocument.root);

        String templateFile = switch (checkedDocument.entryKind) {
            case ARGS -> "ProgramTemplateWithRawCArgs.cpp";
            case NO_ARGS -> "ProgramTemplate.cpp";
            case NONE -> throw new RuntimeException(
                "Internal compiler error: no entry-point function 'main' after successful analysis"
            );
        };

        String template = Files.readString(Path.of(templateFile), StandardCharsets.UTF_8);

        if (!template.contains("__FRANKO_PROGRAM__")) {
            throw new RuntimeException("Template does not contain __FRANKO_PROGRAM__ placeholder.");
        }

        String runtimeHeader = "FrankoRuntime.hpp";
        if (!template.contains("__FRANKO_RUNTIME_HEADER__")) {
            throw new RuntimeException("Template does not contain __FRANKO_RUNTIME_HEADER__ placeholder.");
        }

        String expectedEntryCall = switch (checkedDocument.entryKind) {
            case ARGS -> "FUNC_main_HASH_747cdf98";
            case NO_ARGS -> "FUNC_main_HASH_d7d11c43";
            case NONE -> throw new RuntimeException(
                "Internal compiler error: no entry-point function 'main' after successful analysis"
            );
        };

        if (!template.contains(expectedEntryCall)) {
            throw new RuntimeException(
                "Template '" + templateFile + "' does not call the expected entry point '"
                    + expectedEntryCall + "'."
            );
        }

        String finalCpp = template.replace(
            "__FRANKO_PROGRAM__",
            indentBlock(generatedBody, 0).trim()
        ).replace("__FRANKO_RUNTIME_HEADER__", runtimeHeader);

        Path parent = outPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(outPath, finalCpp, StandardCharsets.UTF_8);
        artifacts.writeDiagnostics(log);

        System.out.println("Successfully compiled to C++14: " + outPath);
    }

    private static PassResult runPass(
            DiagnosticLog log,
            JsonArtifacts artifacts,
            String logName,
            SastPhase phase,
            String heading,
            String warningHeading,
            String failureHeader,
            Supplier<PassResult> pass
    ) {
        if (heading != null) {
            System.out.println(heading);
        }

        PassResult result = pass.get();
        log.add(logName, result.diagnostics);
        artifacts.writeSast(phase, result.document);

        if (warningHeading != null) {
            printWarnings(warningHeading, result.diagnostics);
        }

        if (result.hasErrors()) {
            abort(log, artifacts, failureHeader, result.diagnostics);
        }

        return result;
    }

    private static void abort(
            DiagnosticLog log,
            JsonArtifacts artifacts,
            String header,
            List<Diagnostic> diagnostics
    ) {
        artifacts.writeDiagnostics(log);

        throw new Abort(DiagnosticBag.formatErrors(header, diagnostics));
    }

    private static void printWarnings(String heading, List<Diagnostic> diagnostics) {
        boolean printed = false;

        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.isError()) {
                continue;
            }

            if (!printed) {
                System.out.println(heading);
                printed = true;
            }

            System.out.println("  - " + diagnostic.format());
        }
    }

    private static String indentBlock(String text, int levels) {
        String indent = "    ".repeat(levels);
        String[] lines = text.split("\\R", -1);

        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (!line.isEmpty()) {
                sb.append(indent).append(line);
            }
            sb.append('\n');
        }

        return sb.toString();
    }
}
