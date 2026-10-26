import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ============================================================================
 * OS FFI INTRINSICS
 * ============================================================================
 *
 * PURPOSE
 *
 * OsFfi owns the compiler side of the `import "franko/os"` foreign-function
 * bridge. The Franko declarations live in import/franko/os.fr (stub bodies
 * only); the real C++ lives in import/franko/os.ffi.cpp as one
 * marker-delimited block per intrinsic, each defining its function under the
 * exact mangled name the backend emits.
 *
 * Responsibilities:
 *
 *   - kindOf: recognize the five intrinsic functions by namespace, name, and
 *     exact parameter/return types. Anything else declared in os::posix or
 *     os::win32 fails loudly: a known intrinsic name with a drifted
 *     signature is an "os.fr/ffi.cpp drift" error, and any other function in
 *     those namespaces is an "unsupported function" error. Stubs must never
 *     compile silently.
 *
 *   - bridge parsing: split ffi.cpp into its `// @@franko-ffi:<block>` units
 *     and assemble the splice text (common block first, then one block per
 *     used intrinsic).
 *
 *   - bridge verification: check that every intrinsic present in the program
 *     has its mangled definition present in ffi.cpp, so a mangling drift
 *     fails at Franko-compile time instead of link time.
 *
 * Current intrinsics:
 *
 *   os::posix::read(int32_t, addr<array<byte>>, uint64_t) -> int64_t
 *   os::posix::write(int32_t, addr<array<byte>>, uint64_t) -> int64_t
 *   os::win32::GetStdHandle(uint32_t) -> uint64_t
 *   os::win32::ReadFile(uint64_t, addr<array<byte>>, uint32_t, addr<uint32_t>) -> int32_t
 *   os::win32::WriteFile(uint64_t, addr<array<byte>>, uint32_t, addr<uint32_t>) -> int32_t
 *
 * Current target constants (namespace os, all byte):
 *
 *   IS_LINUX, IS_WINDOWS, IS_MACOS, IS_X86_64, IS_ARM64
 */
final class OsFfi {

    enum Kind {
        POSIX_READ,
        POSIX_WRITE,
        WIN32_GETSTDHANDLE,
        WIN32_READFILE,
        WIN32_WRITEFILE
    }

    static final String OS_NAMESPACE = "os";

    /**
     * The os::IS_* compile-time target constants declared in os.fr.
     *
     * These start life as zero-initialized placeholder globals. When the
     * program imports "franko/os", applyTargetConstants rewrites their
     * initializers to the compilation target's 0/1 values, and
     * SemanticAnalyzer stamps the matching symbols so every reference
     * folds into an integer literal.
     */
    static final List<String> TARGET_CONSTANT_NAMES = List.of(
            "IS_LINUX",
            "IS_WINDOWS",
            "IS_MACOS",
            "IS_X86_64",
            "IS_ARM64"
    );

    static final String POSIX_NAMESPACE = "os::posix";
    static final String WIN32_NAMESPACE = "os::win32";

    private static final String BLOCK_COMMON = "common";
    private static final String BLOCK_PREFIX = "// @@franko-ffi:";
    private static final String BLOCK_END = "// @@franko-ffi:end";

    private OsFfi() {
    }

    // ============================================================
    // Intrinsic Recognition
    // ============================================================

    /**
     * Returns the intrinsic kind of a function declared in os::posix or
     * os::win32, or null for functions outside those namespaces.
     *
     * A known intrinsic name with anything but the exact intrinsic
     * signature throws: os.fr drifted out of sync with ffi.cpp and the
     * bridge would otherwise miscompile. Any other function name in those
     * namespaces also throws: those namespaces are compiler-owned, and an
     * unknown function there would compile its stub body silently.
     */
    static Kind kindOf(FunctionSymbol fn) {
        if (fn == null || fn.namespace == null) {
            return null;
        }

        if (fn.namespace.equals(POSIX_NAMESPACE)) {
            return matchPosix(fn);
        }

        if (fn.namespace.equals(WIN32_NAMESPACE)) {
            return matchWin32(fn);
        }

        return null;
    }

    private static Kind matchPosix(FunctionSymbol fn) {
        if (fn.name.equals("read")) {
            if (!isPosixReadWriteSignature(fn)) {
                throw new IllegalStateException(ffiDriftError(fn));
            }
            return Kind.POSIX_READ;
        }

        if (fn.name.equals("write")) {
            if (!isPosixReadWriteSignature(fn)) {
                throw new IllegalStateException(ffiDriftError(fn));
            }
            return Kind.POSIX_WRITE;
        }

        throw new IllegalStateException(ffiUnsupportedError(fn));
    }

    private static Kind matchWin32(FunctionSymbol fn) {
        if (fn.name.equals("GetStdHandle")) {
            if (!isGetStdHandleSignature(fn)) {
                throw new IllegalStateException(ffiDriftError(fn));
            }
            return Kind.WIN32_GETSTDHANDLE;
        }

        if (fn.name.equals("ReadFile")) {
            if (!isWin32ReadWriteSignature(fn)) {
                throw new IllegalStateException(ffiDriftError(fn));
            }
            return Kind.WIN32_READFILE;
        }

        if (fn.name.equals("WriteFile")) {
            if (!isWin32ReadWriteSignature(fn)) {
                throw new IllegalStateException(ffiDriftError(fn));
            }
            return Kind.WIN32_WRITEFILE;
        }

        throw new IllegalStateException(ffiUnsupportedError(fn));
    }

    private static boolean isPosixReadWriteSignature(FunctionSymbol fn) {
        return fn.parameterTypes().size() == 3
                && isPrimitive(fn.parameterTypes().get(0), SemanticPrimitiveKind.INT32)
                && isByteBufferAddr(fn.parameterTypes().get(1))
                && isPrimitive(fn.parameterTypes().get(2), SemanticPrimitiveKind.UINT64)
                && isPrimitive(fn.returnType(), SemanticPrimitiveKind.INT64);
    }

    private static boolean isGetStdHandleSignature(FunctionSymbol fn) {
        return fn.parameterTypes().size() == 1
                && isPrimitive(fn.parameterTypes().get(0), SemanticPrimitiveKind.UINT32)
                && isPrimitive(fn.returnType(), SemanticPrimitiveKind.UINT64);
    }

    private static boolean isWin32ReadWriteSignature(FunctionSymbol fn) {
        return fn.parameterTypes().size() == 4
                && isPrimitive(fn.parameterTypes().get(0), SemanticPrimitiveKind.UINT64)
                && isByteBufferAddr(fn.parameterTypes().get(1))
                && isPrimitive(fn.parameterTypes().get(2), SemanticPrimitiveKind.UINT32)
                && isUint32Addr(fn.parameterTypes().get(3))
                && isPrimitive(fn.returnType(), SemanticPrimitiveKind.INT32);
    }

    private static boolean isPrimitive(SemanticType type, SemanticPrimitiveKind kind) {
        return new SemanticPrimitiveType(kind).equals(type);
    }

    private static boolean isByteBufferAddr(SemanticType type) {
        return new SemanticAddrType(
                new SemanticDynamicArrayType(
                        new SemanticPrimitiveType(SemanticPrimitiveKind.UINT8)
                )
        ).equals(type);
    }

    private static boolean isUint32Addr(SemanticType type) {
        return new SemanticAddrType(
                new SemanticPrimitiveType(SemanticPrimitiveKind.UINT32)
        ).equals(type);
    }

    private static String ffiDriftError(FunctionSymbol fn) {
        return "FFI error: '"
                + fn.qualifiedName()
                + "' has signature '"
                + fn.fullSignatureString()
                + "' but the os FFI bridge expects the intrinsic signature from"
                + " import/franko/os.fr: os.fr drifted out of sync with"
                + " import/franko/os.ffi.cpp";
    }

    private static String ffiUnsupportedError(FunctionSymbol fn) {
        return "FFI error: unsupported function '"
                + fn.qualifiedName()
                + "' with signature '"
                + fn.fullSignatureString()
                + "': only read/write in os::posix and GetStdHandle/ReadFile/WriteFile"
                + " in os::win32 are FFI intrinsics bridged by import/franko/os.ffi.cpp";
    }

    // ============================================================
    // Target Constants
    // ============================================================

    /**
     * Whether a variable is one of the os::IS_* compile-time constants.
     */
    static boolean isTargetConstantName(String namespacePath, String name) {
        return OS_NAMESPACE.equals(namespacePath)
                && TARGET_CONSTANT_NAMES.contains(name);
    }

    /**
     * Rewrites the os::IS_* placeholder initializers in place to the
     * compilation target's 0/1 values.
     *
     * Must run on the import-resolved AST before desugaring, so the
     * desugarer folds the rewritten initializers and the backend emits
     * the constants as statically-initialized C++ globals.
     *
     * Only the top-level `os` namespace is touched. Every one of the five
     * constants must be present and declared as `byte`; anything else is
     * an "os.fr drifted out of sync with the compiler" error, mirroring
     * the intrinsic drift errors below.
     *
     * Call only when the program imports "franko/os" through its
     * canonical specifier: a user-defined `os` namespace in a program
     * without that import is left alone.
     */
    static void applyTargetConstants(ASTNode program, TargetInfo target) {
        if (!(program instanceof ProgramNode root)) {
            return;
        }

        Set<String> found = new LinkedHashSet<>();
        applyTargetConstantsInItems(root.topLevelItems, null, target, found);

        List<String> missing = new ArrayList<>();

        for (String name : TARGET_CONSTANT_NAMES) {
            if (!found.contains(name)) {
                missing.add(OS_NAMESPACE + "::" + name);
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "FFI error: os.fr is missing target constant(s) "
                            + String.join(", ", missing)
                            + ": os.fr drifted out of sync with the compiler"
                            + " (namespace os must declare byte IS_LINUX,"
                            + " IS_WINDOWS, IS_MACOS, IS_X86_64, and IS_ARM64)"
            );
        }
    }

    private static void applyTargetConstantsInItems(
            List<ASTNode> items,
            String parentPath,
            TargetInfo target,
            Set<String> found
    ) {
        for (ASTNode item : items) {
            if (item instanceof NamespaceNode namespace) {
                String fullPath = parentPath == null
                        ? namespace.name
                        : parentPath + "::" + namespace.name;
                applyTargetConstantsInItems(
                        namespace.items,
                        fullPath,
                        target,
                        found
                );
                continue;
            }

            if (item instanceof GlobalVarDeclNode global
                    && isTargetConstantName(parentPath, global.name)) {
                if (!(global.type instanceof PrimitiveTypeNode primitive)
                        || primitive.kind != PrimitiveKind.UINT8) {
                    throw new IllegalStateException(
                            "FFI error: '"
                                    + OS_NAMESPACE + "::" + global.name
                                    + "' must be declared as 'byte' in"
                                    + " import/franko/os.fr: os.fr drifted out"
                                    + " of sync with the compiler"
                    );
                }

                global.constInitializer = new IntNode(
                        target.constantFor(global.name).toString(10)
                );
                found.add(global.name);
            }
        }
    }

    /**
     * Rejects a live call to an intrinsic that cannot run on the target.
     *
     * Each intrinsic surface is restricted to the targets its bridge
     * blocks support: the win32 blocks are #error off Windows, the posix
     * blocks are #error on Windows (there is no _read/_write fallback).
     * Must be checked at a point where constant-dead branches have
     * already been pruned, so a call inside a target guard such as
     * `if os::IS_WINDOWS == 1` never reaches this check when the guard
     * selects the other target.
     */
    static void checkTargetAllowed(Kind kind, TargetInfo target) {
        if (target == null) {
            return;
        }

        switch (kind) {
            case POSIX_READ, POSIX_WRITE -> {
                if (target.isWindows()) {
                    throw new IllegalStateException(
                            "FFI error: '"
                                    + ffiQualifiedName(kind)
                                    + "' is only available when targeting"
                                    + " linux or macos (target: "
                                    + target.canonical()
                                    + ")"
                    );
                }
            }
            case WIN32_GETSTDHANDLE, WIN32_READFILE, WIN32_WRITEFILE -> {
                if (!target.isWindows()) {
                    throw new IllegalStateException(
                            "FFI error: '"
                                    + ffiQualifiedName(kind)
                                    + "' is only available when targeting"
                                    + " windows (target: "
                                    + target.canonical()
                                    + ")"
                    );
                }
            }
        }
    }

    private static String ffiQualifiedName(Kind kind) {
        return switch (kind) {
            case POSIX_READ -> "os::posix::read";
            case POSIX_WRITE -> "os::posix::write";
            case WIN32_GETSTDHANDLE -> "os::win32::GetStdHandle";
            case WIN32_READFILE -> "os::win32::ReadFile";
            case WIN32_WRITEFILE -> "os::win32::WriteFile";
        };
    }

    // ============================================================
    // Mangled Names
    // ============================================================

    /**
     * The exact C++ definition name each bridge block must provide.
     *
     * These are FunctionSymbol.emittedLocalName() values for the intrinsic
     * signatures above (FNV-1a hash of the canonical signature key),
     * pinned here so Main can sanity-check ffi.cpp the same way it checks
     * the program template's entry-point call.
     */
    static String expectedEmittedName(Kind kind) {
        return switch (kind) {
            case POSIX_READ -> "FUNC_read_HASH_10a3784c";
            case POSIX_WRITE -> "FUNC_write_HASH_10a3784c";
            case WIN32_GETSTDHANDLE -> "FUNC_GetStdHandle_HASH_493245c2";
            case WIN32_READFILE -> "FUNC_ReadFile_HASH_5775278d";
            case WIN32_WRITEFILE -> "FUNC_WriteFile_HASH_5775278d";
        };
    }

    static String blockName(Kind kind) {
        return switch (kind) {
            case POSIX_READ -> "posix.read";
            case POSIX_WRITE -> "posix.write";
            case WIN32_GETSTDHANDLE -> "win32.GetStdHandle";
            case WIN32_READFILE -> "win32.ReadFile";
            case WIN32_WRITEFILE -> "win32.WriteFile";
        };
    }

    // ============================================================
    // Bridge Parsing
    // ============================================================

    /**
     * Splits bridge file text into its marker-delimited blocks.
     *
     * A block starts at a line exactly equal to
     * `// @@franko-ffi:<name>` and runs until the next line exactly equal
     * to `// @@franko-ffi:end`. Marker lines themselves are excluded.
     * Duplicate block names are an error.
     */
    static Map<String, String> parseBlocks(String bridgeText) {
        Map<String, String> blocks = new LinkedHashMap<>();

        String currentName = null;
        StringBuilder current = new StringBuilder();

        for (String line : bridgeText.split("\\R", -1)) {
            if (line.equals(BLOCK_END)) {
                if (currentName == null) {
                    throw new IllegalStateException(
                            "FFI error: ffi.cpp has '"
                                    + BLOCK_END
                                    + "' without an open block"
                    );
                }
                if (blocks.containsKey(currentName)) {
                    throw new IllegalStateException(
                            "FFI error: ffi.cpp defines block '"
                                    + currentName
                                    + "' more than once"
                    );
                }
                blocks.put(currentName, current.toString());
                currentName = null;
                current.setLength(0);
                continue;
            }

            if (line.startsWith(BLOCK_PREFIX)) {
                if (currentName != null) {
                    throw new IllegalStateException(
                            "FFI error: ffi.cpp opens block '"
                                    + line.substring(BLOCK_PREFIX.length())
                                    + "' before closing block '"
                                    + currentName
                                    + "'"
                    );
                }
                currentName = line.substring(BLOCK_PREFIX.length());
                continue;
            }

            if (currentName != null) {
                current.append(line).append('\n');
            }
        }

        if (currentName != null) {
            throw new IllegalStateException(
                    "FFI error: ffi.cpp block '"
                            + currentName
                            + "' is never closed with '"
                            + BLOCK_END
                            + "'"
            );
        }

        return blocks;
    }

    /**
     * Assembles the splice text for the used intrinsics: the common block
     * first, then one block per used kind in first-use order.
     */
    static String buildBridge(String bridgeText, Set<Kind> used) {
        Map<String, String> blocks = parseBlocks(bridgeText);

        String common = blocks.get(BLOCK_COMMON);

        if (common == null) {
            throw new IllegalStateException(
                    "FFI error: ffi.cpp is missing the '"
                            + BLOCK_COMMON
                            + "' block"
            );
        }

        StringBuilder sb = new StringBuilder();
        sb.append(common);

        if (!common.endsWith("\n")) {
            sb.append('\n');
        }

        for (Kind kind : used) {
            String block = blocks.get(blockName(kind));

            if (block == null) {
                throw new IllegalStateException(
                        "FFI error: ffi.cpp is missing the '"
                                + blockName(kind)
                                + "' block needed by '"
                                + expectedEmittedName(kind)
                                + "'"
                );
            }

            sb.append(block);

            if (!block.endsWith("\n")) {
                sb.append('\n');
            }
        }

        return sb.toString();
    }

    // ============================================================
    // Bridge Verification
    // ============================================================

    /**
     * Collects the intrinsic kinds declared anywhere in the semantic
     * program, in declaration order.
     *
     * Throws the same drift/unsupported errors as kindOf, so a bad os
     * namespace fails fast before codegen.
     */
    static Set<Kind> collectKinds(SemanticASTNode root) {
        Set<Kind> kinds = new LinkedHashSet<>();
        collectKindsInto(root, kinds);
        return kinds;
    }

    private static void collectKindsInto(SemanticASTNode node, Set<Kind> out) {
        if (node instanceof SemanticProgramNode program) {
            for (SemanticASTNode item : program.topLevelItems) {
                collectKindsInto(item, out);
            }
            return;
        }

        if (node instanceof SemanticNamespaceNode namespace) {
            for (SemanticASTNode item : namespace.items) {
                collectKindsInto(item, out);
            }
            return;
        }

        if (node instanceof SemanticStructDeclNode struct) {
            for (SemanticFunctionDeclNode method : struct.methods) {
                collectKindsInto(method, out);
            }
            return;
        }

        if (node instanceof SemanticFunctionDeclNode fn) {
            Kind kind = kindOf(fn.symbol);

            if (kind != null) {
                String emitted = fn.symbol.emittedLocalName();
                String expected = expectedEmittedName(kind);

                if (!emitted.equals(expected)) {
                    throw new IllegalStateException(
                            "FFI error: '"
                                    + fn.symbol.qualifiedName()
                                    + "' emits as '"
                                    + emitted
                                    + "' but ffi.cpp provides '"
                                    + expected
                                    + "': mangling drift between os.fr and ffi.cpp"
                    );
                }

                out.add(kind);
            }
        }
    }

    /**
     * Verifies that every intrinsic declared in the program has its mangled
     * definition present in the bridge text.
     */
    static void verifyBridge(SemanticASTNode root, String bridgeText) {
        Set<Kind> kinds = collectKinds(root);

        List<String> missing = new ArrayList<>();

        for (Kind kind : kinds) {
            if (!bridgeText.contains(expectedEmittedName(kind))) {
                missing.add(expectedEmittedName(kind));
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "FFI error: ffi.cpp is missing mangled definition(s) "
                            + String.join(", ", missing)
                            + ": the bridge drifted out of sync with os.fr"
            );
        }
    }
}
