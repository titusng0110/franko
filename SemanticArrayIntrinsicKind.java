import java.util.Objects;

enum SemanticIntrinsicArgKind {
    BYTE(SemanticPrimitiveKind.UINT8),
    UINT16(SemanticPrimitiveKind.UINT16),
    UINT32(SemanticPrimitiveKind.UINT32),
    UINT64(SemanticPrimitiveKind.UINT64),
    INT16(SemanticPrimitiveKind.INT16),
    INT32(SemanticPrimitiveKind.INT32),
    INT64(SemanticPrimitiveKind.INT64),
    POINTER(null);

    public final SemanticPrimitiveKind primitiveKind;

    SemanticIntrinsicArgKind(SemanticPrimitiveKind primitiveKind) {
        this.primitiveKind = primitiveKind;
    }

    public boolean isPointer() {
        return primitiveKind == null;
    }

    public SastType primitiveType() {
        if (primitiveKind == null) {
            throw new IllegalStateException(
                    "pointer intrinsic argument has no primitive type"
            );
        }

        return SastType.primitive(primitiveKind);
    }
}

enum SemanticIntrinsicElementRule {
    NONE,
    MEMSET_SAFE,
    MEMCPY_SAFE,
    BYTE
}

enum SemanticArrayIntrinsicKind {
    INIT("init", SemanticPrimitiveKind.INT32, true,
            SemanticIntrinsicElementRule.NONE, PtrPermission.OWNER,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    INIT_ZERO("init_zero", SemanticPrimitiveKind.INT32, true,
            SemanticIntrinsicElementRule.NONE, PtrPermission.OWNER,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    RESIZE("resize", SemanticPrimitiveKind.INT32, true,
            SemanticIntrinsicElementRule.NONE, PtrPermission.OWNER,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),

    UNINIT("uninit", null, true,
            SemanticIntrinsicElementRule.NONE, PtrPermission.OWNER,
            new int[]{0}),

    MEMSET("memset", null, false,
            SemanticIntrinsicElementRule.MEMSET_SAFE, PtrPermission.MUTABLE,
            new int[]{1, 3},
            SemanticIntrinsicArgKind.BYTE,
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32),
    MEMCPY("memcpy", null, false,
            SemanticIntrinsicElementRule.MEMCPY_SAFE, PtrPermission.MUTABLE,
            new int[]{1, 4},
            SemanticIntrinsicArgKind.POINTER,
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32),
    MEMMOVE("memmove", null, false,
            SemanticIntrinsicElementRule.MEMCPY_SAFE, PtrPermission.MUTABLE,
            new int[]{3},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32),

    LENGTH("length", SemanticPrimitiveKind.UINT32, false,
            SemanticIntrinsicElementRule.NONE, PtrPermission.READ_ONLY,
            new int[]{0}),

    MEMCMP("memcmp", SemanticPrimitiveKind.INT32, false,
            SemanticIntrinsicElementRule.MEMCPY_SAFE, PtrPermission.READ_ONLY,
            new int[]{1, 4},
            SemanticIntrinsicArgKind.POINTER,
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32),

    MEMCHR("memchr", SemanticPrimitiveKind.UINT32, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1, 3},
            SemanticIntrinsicArgKind.BYTE,
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32),
    MEMMEM("memmem", SemanticPrimitiveKind.UINT32, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1, 3},
            SemanticIntrinsicArgKind.POINTER,
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32),

    READ_UINT16_LE("read_uint16_LE", SemanticPrimitiveKind.UINT16, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    READ_UINT16_BE("read_uint16_BE", SemanticPrimitiveKind.UINT16, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    WRITE_UINT16_LE("write_uint16_LE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT16),
    WRITE_UINT16_BE("write_uint16_BE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT16),

    READ_UINT32_LE("read_uint32_LE", SemanticPrimitiveKind.UINT32, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    READ_UINT32_BE("read_uint32_BE", SemanticPrimitiveKind.UINT32, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    WRITE_UINT32_LE("write_uint32_LE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32),
    WRITE_UINT32_BE("write_uint32_BE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT32),

    READ_UINT64_LE("read_uint64_LE", SemanticPrimitiveKind.UINT64, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    READ_UINT64_BE("read_uint64_BE", SemanticPrimitiveKind.UINT64, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    WRITE_UINT64_LE("write_uint64_LE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT64),
    WRITE_UINT64_BE("write_uint64_BE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.UINT64),

    READ_INT16_LE("read_int16_LE", SemanticPrimitiveKind.INT16, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    READ_INT16_BE("read_int16_BE", SemanticPrimitiveKind.INT16, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    WRITE_INT16_LE("write_int16_LE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.INT16),
    WRITE_INT16_BE("write_int16_BE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.INT16),

    READ_INT32_LE("read_int32_LE", SemanticPrimitiveKind.INT32, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    READ_INT32_BE("read_int32_BE", SemanticPrimitiveKind.INT32, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    WRITE_INT32_LE("write_int32_LE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.INT32),
    WRITE_INT32_BE("write_int32_BE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.INT32),

    READ_INT64_LE("read_int64_LE", SemanticPrimitiveKind.INT64, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    READ_INT64_BE("read_int64_BE", SemanticPrimitiveKind.INT64, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT32),
    WRITE_INT64_LE("write_int64_LE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.INT64),
    WRITE_INT64_BE("write_int64_BE", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT32,
            SemanticIntrinsicArgKind.INT64),

    READ_BIT("read_bit", SemanticPrimitiveKind.UINT8, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{1}, SemanticIntrinsicArgKind.UINT64),
    WRITE_BIT("write_bit", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{2},
            SemanticIntrinsicArgKind.UINT64,
            SemanticIntrinsicArgKind.BYTE),
    FLIP_BIT("flip_bit", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{1}, SemanticIntrinsicArgKind.UINT64),

    BITWISE_NOT_MASK("bitwise_not_mask", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{0}),
    BITWISE_XOR_MASK("bitwise_xor_mask", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{1}, SemanticIntrinsicArgKind.BYTE),
    BITWISE_AND_MASK("bitwise_and_mask", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{1}, SemanticIntrinsicArgKind.BYTE),
    BITWISE_OR_MASK("bitwise_or_mask", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{1}, SemanticIntrinsicArgKind.BYTE),

    SHIFT_BITS_LEFT("shift_bits_left", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{1}, SemanticIntrinsicArgKind.UINT64),
    SHIFT_BITS_RIGHT("shift_bits_right", null, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.MUTABLE,
            new int[]{1}, SemanticIntrinsicArgKind.UINT64),

    COUNT_SET_BITS("count_set_bits", SemanticPrimitiveKind.UINT64, false,
            SemanticIntrinsicElementRule.BYTE, PtrPermission.READ_ONLY,
            new int[]{0});

    public final String runtimeName;
    public final SemanticPrimitiveKind returnKind;
    public final boolean dynamicOnly;
    public final SemanticIntrinsicElementRule elementRule;
    public final PtrPermission permission;
    public final int[] allowedArities;
    public final SemanticIntrinsicArgKind[] argKinds;

    SemanticArrayIntrinsicKind(
            String runtimeName,
            SemanticPrimitiveKind returnKind,
            boolean dynamicOnly,
            SemanticIntrinsicElementRule elementRule,
            PtrPermission permission,
            int[] allowedArities,
            SemanticIntrinsicArgKind... argKinds
    ) {
        this.runtimeName = Objects.requireNonNull(runtimeName);
        this.returnKind = returnKind;
        this.dynamicOnly = dynamicOnly;
        this.elementRule = Objects.requireNonNull(elementRule);
        this.permission = Objects.requireNonNull(permission);
        this.allowedArities = allowedArities;
        this.argKinds = argKinds;
    }

    public static SemanticArrayIntrinsicKind fromRuntimeName(String name) {
        for (SemanticArrayIntrinsicKind kind : values()) {
            if (kind.runtimeName.equals(name)) {
                return kind;
            }
        }

        return null;
    }

    public SastType returnType() {
        if (returnKind == null) {
            return SastType.voidType();
        }

        return SastType.primitive(returnKind);
    }

    public boolean returnsVoid() {
        return returnKind == null;
    }

    public boolean returnsStatus() {
        return this == INIT || this == INIT_ZERO || this == RESIZE;
    }

    public boolean requiresStorageLValue() {
        return permission != PtrPermission.READ_ONLY;
    }

    public int maxArity() {
        int max = 0;

        for (int arity : allowedArities) {
            max = Math.max(max, arity);
        }

        return max;
    }

    public boolean acceptsArity(int arity) {
        for (int allowed : allowedArities) {
            if (allowed == arity) {
                return true;
            }
        }

        return false;
    }

    public SemanticIntrinsicArgKind argKind(int index) {
        if (index < 0 || index >= argKinds.length) {
            throw new IllegalStateException(
                    "intrinsic '" + runtimeName + "' has no argument " + index
            );
        }

        return argKinds[index];
    }

    public boolean hasVariadicArity() {
        return allowedArities.length > 1;
    }

    public String arityDescription() {
        if (allowedArities.length == 1) {
            return "exactly " + allowedArities[0];
        }

        return "either " + allowedArities[0] + " or " + allowedArities[1];
    }
}
