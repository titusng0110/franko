import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
            printDiagnosticReport(log);
            System.exit(1);
        } catch (RuntimeException e) {
            log.add("internal", Diagnostic.error(
                    e.getMessage() == null ? e.getClass().getName() : e.getMessage()
            ));
            artifacts.writeDiagnostics(log);
            printDiagnosticReport(log);
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
        Frontend frontend = new Frontend(artifacts, modules, target);

        System.out.println("==== Parsing ====");
        Result<AstDocument> rawResult = frontend.parseFile(sourceFile);
        log.add("parse", rawResult.diagnostics);

        if (rawResult.value == null) {
            abort(log, artifacts);
        }

        AstDocument raw = rawResult.value;
        artifacts.writeAst(AstPhase.RAW, raw);

        if (rawResult.hasErrors()) {
            abort(log, artifacts);
        }

        System.out.println("==== Desugaring ====");
        Result<AstDocument> desugaredResult = frontend.desugar(raw);
        log.add("desugar", desugaredResult.diagnostics);

        if (desugaredResult.value == null) {
            abort(log, artifacts);
        }

        AstDocument desugared = desugaredResult.value;
        artifacts.writeAst(AstPhase.DESUGARED, desugared);

        if (desugaredResult.hasErrors()) {
            abort(log, artifacts);
        }

        ImportResolver importResolver = new ImportResolver(
                importSearchDir,
                frontend
        );
        System.out.println("==== Resolving Imports ====");
        ImportResult importResult = importResolver.resolve(desugared, sourceFile);
        log.add("imports", importResult.diagnostics);
        artifacts.writeAst(AstPhase.IMPORT_RESOLVED, importResult.document);

        if (importResult.hasErrors()) {
            abort(log, artifacts);
        }

        PassResult semaResult = runPass(
                log,
                artifacts,
                "semantic",
                SastPhase.SEMANTIC,
                "==== Semantic Analyzing ====",
                PassCall.semantic(target, importResult.document)
        );

        System.out.println("==== Useless Generic Pruning ====");
        SastDocument genericPruned =
                new UselessGenericPruner().prune(semaResult.document);
        artifacts.writeSast(SastPhase.USELESS_GENERIC_PRUNED, genericPruned);

        PassResult legalityResult = runPass(
                log,
                artifacts,
                "legality",
                SastPhase.LEGALITY,
                "==== Legality Checking ====",
                PassCall.legality(genericPruned)
        );

        System.out.println("==== Branch Pruning ====");
        SastDocument branchPruned = new BranchPruner().prune(legalityResult.document);
        artifacts.writeSast(SastPhase.BRANCH_PRUNED, branchPruned);

        SastDocument inactivePruned = branchPruned;

        System.out.println("==== Inactive Declaration Pruning ====");
        for (int i = 0; i < 2; i++) {
            inactivePruned =
                    new InactiveDeclarationPruner().prune(inactivePruned);
        }

        artifacts.writeSast(SastPhase.INACTIVE_PRUNED, inactivePruned);

        MemoryAnalysisContext memoryContext =
                MemoryAnalysisContext.build(inactivePruned.root);

        PassResult pointerStateResult = runPass(
                log,
                artifacts,
                "null-state",
                SastPhase.NULL_STATE,
                "==== Pointer Null-State Checking ====",
                PassCall.nullState(inactivePruned, memoryContext)
        );

        PassResult danglingResult = runPass(
                log,
                artifacts,
                "dangling",
                SastPhase.DANGLING,
                "==== Dangling Pointer Checking ====",
                PassCall.dangling(pointerStateResult.document, memoryContext)
        );

        PassResult dynamicArrayResult = runPass(
                log,
                artifacts,
                "dyn-array",
                SastPhase.DYN_ARRAY,
                "==== Dynamic Array Memory Checking ====",
                PassCall.dynamicArray(danglingResult.document, memoryContext)
        );

        PassResult permissionResult = runPass(
                log,
                artifacts,
                "permissions",
                SastPhase.PERMISSIONS,
                "==== Pointer Permission Linting ====",
                PassCall.permissions(dynamicArrayResult.document)
        );

        PassResult privateIdentifierResult = runPass(
                log,
                artifacts,
                "private-id",
                SastPhase.PRIVATE_ID,
                "==== Private Identifier Linting ====",
                PassCall.privateIdentifiers(permissionResult.document)
        );

        FfiHandler ffiHandler = new FfiHandler(modules);
        SastDocument checkedDocument = privateIdentifierResult.document;

        if (modules.hasFfiEntities()) {
            System.out.println("==== FFI Verifying ====");
            PassResult ffiResult = ffiHandler.verify(privateIdentifierResult.document);
            log.add("ffi-verified", ffiResult.diagnostics);
            artifacts.writeSast(SastPhase.FFI_VERIFIED, ffiResult.document);

            if (ffiResult.hasErrors()) {
                abort(log, artifacts);
            }

            checkedDocument = ffiResult.document;
        }

        checkedDocument = checkedDocument.withPhase(SastPhase.CHECKED);
        artifacts.writeSast(SastPhase.CHECKED, checkedDocument);

        FfiHandler activeFfiHandler = modules.hasFfiEntities()
                ? ffiHandler
                : null;
        Cpp14Codegen codegen = new Cpp14Codegen(activeFfiHandler, maxOptimize);
        String generatedBody = codegen.generate(checkedDocument.root);

        String templateFile;

        if (checkedDocument.entryKind == EntryKind.ARGS) {
            templateFile = "ProgramTemplateWithRawCArgs.cpp";
        } else if (checkedDocument.entryKind == EntryKind.NO_ARGS) {
            templateFile = "ProgramTemplate.cpp";
        } else {
            throw new RuntimeException(
                    "Internal compiler error: no entry-point function 'main' after successful analysis"
            );
        }

        String template = Files.readString(Path.of(templateFile), StandardCharsets.UTF_8);

        if (!template.contains("__FRANKO_PROGRAM__")) {
            throw new RuntimeException("Template does not contain __FRANKO_PROGRAM__ placeholder.");
        }

        String runtimeHeader = "FrankoRuntime.hpp";
        if (!template.contains("__FRANKO_RUNTIME_HEADER__")) {
            throw new RuntimeException("Template does not contain __FRANKO_RUNTIME_HEADER__ placeholder.");
        }

        String expectedEntryCall;

        if (checkedDocument.entryKind == EntryKind.ARGS) {
            expectedEntryCall = "FUNC_main_b3b049d8";
        } else if (checkedDocument.entryKind == EntryKind.NO_ARGS) {
            expectedEntryCall = "FUNC_main_72623263";
        } else {
            throw new RuntimeException(
                    "Internal compiler error: no entry-point function 'main' after successful analysis"
            );
        }

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
        printDiagnosticReport(log);

        System.out.println("Successfully compiled to C++14: " + outPath);
    }

    private enum PassKind {
        SEMANTIC,
        LEGALITY,
        NULL_STATE,
        DANGLING,
        DYNAMIC_ARRAY,
        PERMISSIONS,
        PRIVATE_ID
    }

    private static final class PassCall {
        final PassKind kind;
        final TargetInfo target;
        final AstDocument astDocument;
        final SastDocument document;
        final MemoryAnalysisContext memoryContext;

        private PassCall(
                PassKind kind,
                TargetInfo target,
                AstDocument astDocument,
                SastDocument document,
                MemoryAnalysisContext memoryContext
        ) {
            this.kind = kind;
            this.target = target;
            this.astDocument = astDocument;
            this.document = document;
            this.memoryContext = memoryContext;
        }

        static PassCall semantic(TargetInfo target, AstDocument document) {
            return new PassCall(PassKind.SEMANTIC, target, document, null, null);
        }

        static PassCall legality(SastDocument document) {
            return new PassCall(PassKind.LEGALITY, null, null, document, null);
        }

        static PassCall nullState(
                SastDocument document,
                MemoryAnalysisContext memoryContext
        ) {
            return new PassCall(
                    PassKind.NULL_STATE, null, null, document, memoryContext);
        }

        static PassCall dangling(
                SastDocument document,
                MemoryAnalysisContext memoryContext
        ) {
            return new PassCall(
                    PassKind.DANGLING, null, null, document, memoryContext);
        }

        static PassCall dynamicArray(
                SastDocument document,
                MemoryAnalysisContext memoryContext
        ) {
            return new PassCall(
                    PassKind.DYNAMIC_ARRAY, null, null, document, memoryContext);
        }

        static PassCall permissions(SastDocument document) {
            return new PassCall(PassKind.PERMISSIONS, null, null, document, null);
        }

        static PassCall privateIdentifiers(SastDocument document) {
            return new PassCall(PassKind.PRIVATE_ID, null, null, document, null);
        }

        PassResult run() {
            if (kind == PassKind.SEMANTIC) {
                return new SemanticAnalyzer(target).analyze(astDocument);
            }

            if (kind == PassKind.LEGALITY) {
                return new LegalityChecker().check(document);
            }

            if (kind == PassKind.NULL_STATE) {
                return new PointerNullStateChecker(
                        new DiagnosticBag(), memoryContext).check(document);
            }

            if (kind == PassKind.DANGLING) {
                return new DanglingPointerChecker(
                        new DiagnosticBag(), memoryContext).check(document);
            }

            if (kind == PassKind.DYNAMIC_ARRAY) {
                return new DynamicArrayMemoryChecker(
                        new DiagnosticBag(), memoryContext).check(document);
            }

            if (kind == PassKind.PERMISSIONS) {
                return new PointerPermissionChecker(
                        new DiagnosticBag()).check(document);
            }

            return new PrivateIdentifierLinter(
                    new DiagnosticBag()).check(document);
        }
    }

    private static PassResult runPass(
            DiagnosticLog log,
            JsonArtifacts artifacts,
            String logName,
            SastPhase phase,
            String heading,
            PassCall pass
    ) {
        if (heading != null) {
            System.out.println(heading);
        }

        PassResult result = pass.run();
        log.add(logName, result.diagnostics);
        artifacts.writeSast(phase, result.document);

        if (result.hasErrors()) {
            abort(log, artifacts);
        }

        return result;
    }

    private static void abort(
            DiagnosticLog log,
            JsonArtifacts artifacts
    ) {
        artifacts.writeDiagnostics(log);
        throw new Abort("");
    }

    private static void printDiagnosticReport(DiagnosticLog log) {
        String report = log.formatReport();

        if (!report.isEmpty()) {
            System.out.println();
            System.out.print(report);
            System.out.println();
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
