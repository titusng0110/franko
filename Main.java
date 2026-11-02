import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    private static final class Abort extends RuntimeException {
        private static final long serialVersionUID = 1L;
        Abort(String message) {
            super(message);
        }
    }

    public static void main(String[] args) throws Exception {
        String sourceFile = "test.fr";
        String outputFile = "test.cpp";
        Path importSearchDir = Path.of("import");
        String targetSpec = null;
        boolean optimize = false;
        boolean maxOptimize = false;
        boolean emitJson = false;

        if (args.length > 0) {
            sourceFile = args[0];
        }

        for (int i = 1; i < args.length; i++) {
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
                    throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
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
        TargetInfo target = targetSpec == null
                ? TargetInfo.detectHost()
                : TargetInfo.parse(targetSpec);

        ModuleRegistry modules = new ModuleRegistry();
        Frontend frontend = new Frontend(artifacts, modules);
        frontend.setTarget(target);

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

        ImportResolver importResolver = new ImportResolver(importSearchDir, frontend);
        ImportResult importResult = importResolver.resolve(desugared, sourceFile);
        log.add("imports", importResult.diagnostics);
        artifacts.writeAst(AstPhase.IMPORT_RESOLVED, importResult.document);

        if (importResult.hasErrors()) {
            abort(log, artifacts, "Import resolution failed:", importResult.diagnostics);
        }

        SemanticAnalyzer sema = new SemanticAnalyzer();
        SastResult semaResult = sema.analyze(importResult.document);
        log.add("semantic", semaResult.diagnostics);
        artifacts.writeSast(SastPhase.SEMANTIC, semaResult.document);
        printWarnings("==== Warnings ====", semaResult.diagnostics);

        if (semaResult.hasErrors()) {
            abort(log, artifacts, "Semantic analysis failed:", semaResult.diagnostics);
        }

        System.out.println("==== Legality Checking ====");
        PassResult legalityResult = new LegalityChecker().check(semaResult.document);
        log.add("legality", legalityResult.diagnostics);
        artifacts.writeSast(SastPhase.LEGALITY, legalityResult.document);

        if (legalityResult.hasErrors()) {
            abort(log, artifacts, "Legality checking failed:", legalityResult.diagnostics);
        }

        SastDocument branchPruned = new BranchPruner().prune(legalityResult.document);
        artifacts.writeSast(SastPhase.BRANCH_PRUNED, branchPruned);

        SastDocument inactivePruned =
                new InactiveDeclarationPruner().prune(branchPruned);
        artifacts.writeSast(SastPhase.INACTIVE_PRUNED, inactivePruned);

        System.out.println("==== Pointer Null-State Checking ====");
        PointerNullStateChecker pointerStateChecker =
                new PointerNullStateChecker(new DiagnosticBag());
        PassResult pointerStateResult = pointerStateChecker.check(inactivePruned);
        log.add("null-state", pointerStateResult.diagnostics);
        artifacts.writeSast(SastPhase.NULL_STATE, pointerStateResult.document);
        printWarnings(
                "==== Pointer Null-State Warnings ====",
                pointerStateResult.diagnostics
        );

        if (pointerStateResult.hasErrors()) {
            abort(
                    log,
                    artifacts,
                    "Pointer null-state checking failed:",
                    pointerStateResult.diagnostics
            );
        }

        System.out.println("==== Dangling Pointer Checking ====");
        DanglingPointerChecker danglingChecker =
                new DanglingPointerChecker(new DiagnosticBag());
        PassResult danglingResult = danglingChecker.check(pointerStateResult.document);
        log.add("dangling", danglingResult.diagnostics);
        artifacts.writeSast(SastPhase.DANGLING, danglingResult.document);
        printWarnings(
                "==== Dangling Pointer Warnings ====",
                danglingResult.diagnostics
        );

        if (danglingResult.hasErrors()) {
            abort(
                    log,
                    artifacts,
                    "Dangling pointer checking failed:",
                    danglingResult.diagnostics
            );
        }

        System.out.println("==== Dynamic Array Memory Checking ====");
        DynamicArrayMemoryChecker dynamicArrayChecker =
                new DynamicArrayMemoryChecker(new DiagnosticBag());
        PassResult dynamicArrayResult = dynamicArrayChecker.check(danglingResult.document);
        log.add("dyn-array", dynamicArrayResult.diagnostics);
        artifacts.writeSast(SastPhase.DYN_ARRAY, dynamicArrayResult.document);
        printWarnings(
                "==== Dynamic Array Memory Warnings ====",
                dynamicArrayResult.diagnostics
        );

        if (dynamicArrayResult.hasErrors()) {
            abort(
                    log,
                    artifacts,
                    "Dynamic array memory checking failed:",
                    dynamicArrayResult.diagnostics
            );
        }

        System.out.println("==== Pointer Permission Linting ====");
        PointerPermissionChecker permissionChecker =
                new PointerPermissionChecker(new DiagnosticBag());
        PassResult permissionResult = permissionChecker.check(
                dynamicArrayResult.document
        );
        log.add("permissions", permissionResult.diagnostics);
        artifacts.writeSast(SastPhase.PERMISSIONS, permissionResult.document);
        printWarnings(
                "==== Pointer Permission Warnings ====",
                permissionResult.diagnostics
        );

        if (permissionResult.hasErrors()) {
            abort(
                    log,
                    artifacts,
                    "Pointer permission linting failed:",
                    permissionResult.diagnostics
            );
        }

        System.out.println("==== Private Identifier Linting ====");
        PrivateIdentifierLinter privateIdentifierLinter =
                new PrivateIdentifierLinter(new DiagnosticBag());
        PassResult privateIdentifierResult = privateIdentifierLinter.check(
                permissionResult.document
        );
        log.add("private-id", privateIdentifierResult.diagnostics);
        artifacts.writeSast(SastPhase.PRIVATE_ID, privateIdentifierResult.document);
        printWarnings(
                "==== Private Identifier Warnings ====",
                privateIdentifierResult.diagnostics
        );

        if (privateIdentifierResult.hasErrors()) {
            abort(
                    log,
                    artifacts,
                    "Private identifier linting failed:",
                    privateIdentifierResult.diagnostics
            );
        }

        FfiHandler ffiHandler = new FfiHandler(modules, target);
        SastDocument checkedDocument = privateIdentifierResult.document;

        if (ffiHandler.hasFfiEntities()) {
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

        FfiCodegen ffiCodegen = ffiHandler.hasFfiEntities()
                ? ffiHandler.codegen()
                : null;
        Cpp14Codegen codegen = new Cpp14Codegen(ffiCodegen, target, maxOptimize);
        String generatedBody = codegen.generate(checkedDocument);

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

    private static void abort(
            DiagnosticLog log,
            JsonArtifacts artifacts,
            String header,
            List<Diagnostic> diagnostics
    ) {
        artifacts.writeDiagnostics(log);

        StringBuilder sb = new StringBuilder(header);

        if (!header.endsWith("\n")) {
            sb.append('\n');
        }

        int index = 0;

        for (Diagnostic diagnostic : diagnostics) {
            if (!diagnostic.isError()) {
                continue;
            }

            index++;
            sb.append("  ")
                    .append(index)
                    .append(". ")
                    .append(diagnostic.format())
                    .append('\n');
        }

        throw new Abort(sb.toString());
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
