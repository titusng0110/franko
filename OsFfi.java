import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class OsFfi {

    enum Kind {
        POSIX_READ,
        POSIX_WRITE,
        WIN32_GETSTDHANDLE,
        WIN32_READFILE,
        WIN32_WRITEFILE
    }

    static final String OS_NAMESPACE = "os";

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
            if (!isPosixReadSignature(fn)) {
                throw new IllegalStateException(ffiDriftError(fn));
            }
            return Kind.POSIX_READ;
        }

        if (fn.name.equals("write")) {
            if (!isPosixWriteSignature(fn)) {
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
            if (!isWin32ReadFileSignature(fn)) {
                throw new IllegalStateException(ffiDriftError(fn));
            }
            return Kind.WIN32_READFILE;
        }

        if (fn.name.equals("WriteFile")) {
            if (!isWin32WriteFileSignature(fn)) {
                throw new IllegalStateException(ffiDriftError(fn));
            }
            return Kind.WIN32_WRITEFILE;
        }

        throw new IllegalStateException(ffiUnsupportedError(fn));
    }

    private static boolean isPosixReadSignature(FunctionSymbol fn) {
        return fn.parameterTypes().size() == 3
                && isPrimitive(fn.parameterTypes().get(0), SemanticPrimitiveKind.INT32)
                && isByteBufferPtr(fn.parameterTypes().get(1), PtrPermission.MUTABLE)
                && isPrimitive(fn.parameterTypes().get(2), SemanticPrimitiveKind.UINT64)
                && isPrimitive(fn.returnType(), SemanticPrimitiveKind.INT64);
    }

    private static boolean isPosixWriteSignature(FunctionSymbol fn) {
        return fn.parameterTypes().size() == 3
                && isPrimitive(fn.parameterTypes().get(0), SemanticPrimitiveKind.INT32)
                && isByteBufferPtr(fn.parameterTypes().get(1), PtrPermission.READ_ONLY)
                && isPrimitive(fn.parameterTypes().get(2), SemanticPrimitiveKind.UINT64)
                && isPrimitive(fn.returnType(), SemanticPrimitiveKind.INT64);
    }

    private static boolean isGetStdHandleSignature(FunctionSymbol fn) {
        return fn.parameterTypes().size() == 1
                && isPrimitive(fn.parameterTypes().get(0), SemanticPrimitiveKind.UINT32)
                && isPrimitive(fn.returnType(), SemanticPrimitiveKind.UINT64);
    }

    private static boolean isWin32ReadFileSignature(FunctionSymbol fn) {
        return fn.parameterTypes().size() == 4
                && isPrimitive(fn.parameterTypes().get(0), SemanticPrimitiveKind.UINT64)
                && isByteBufferPtr(fn.parameterTypes().get(1), PtrPermission.MUTABLE)
                && isPrimitive(fn.parameterTypes().get(2), SemanticPrimitiveKind.UINT32)
                && isUint32Ptr(fn.parameterTypes().get(3), PtrPermission.MUTABLE)
                && isPrimitive(fn.returnType(), SemanticPrimitiveKind.INT32);
    }

    private static boolean isWin32WriteFileSignature(FunctionSymbol fn) {
        return fn.parameterTypes().size() == 4
                && isPrimitive(fn.parameterTypes().get(0), SemanticPrimitiveKind.UINT64)
                && isByteBufferPtr(fn.parameterTypes().get(1), PtrPermission.READ_ONLY)
                && isPrimitive(fn.parameterTypes().get(2), SemanticPrimitiveKind.UINT32)
                && isUint32Ptr(fn.parameterTypes().get(3), PtrPermission.MUTABLE)
                && isPrimitive(fn.returnType(), SemanticPrimitiveKind.INT32);
    }

    private static boolean isPrimitive(SemanticType type, SemanticPrimitiveKind kind) {
        return new SemanticPrimitiveType(kind).equals(type);
    }

    private static boolean isByteBufferPtr(SemanticType type, PtrPermission permission) {
        return new SemanticPtrType(
                new SemanticDynamicArrayType(
                        new SemanticPrimitiveType(SemanticPrimitiveKind.UINT8)
                )
        ).withPermission(permission).equals(type);
    }

    private static boolean isUint32Ptr(SemanticType type, PtrPermission permission) {
        return new SemanticPtrType(
                new SemanticPrimitiveType(SemanticPrimitiveKind.UINT32)
        ).withPermission(permission).equals(type);
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

    static boolean isTargetConstantName(String namespacePath, String name) {
        return OS_NAMESPACE.equals(namespacePath)
                && TARGET_CONSTANT_NAMES.contains(name);
    }

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

    static String expectedEmittedName(Kind kind) {
        return switch (kind) {
            case POSIX_READ -> "FUNC_read_HASH_247cb6de";
            case POSIX_WRITE -> "FUNC_write_HASH_3a46712f";
            case WIN32_GETSTDHANDLE -> "FUNC_GetStdHandle_HASH_31d0af80";
            case WIN32_READFILE -> "FUNC_ReadFile_HASH_955d9645";
            case WIN32_WRITEFILE -> "FUNC_WriteFile_HASH_ab68bdd0";
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
