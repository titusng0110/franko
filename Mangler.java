// ============================================================
// Mangler
// ============================================================

/**
 * Computes the 32-bit hash suffix used in emitted C++ names.
 *
 * Emitted function and method names carry an 8-hex-digit suffix:
 *
 *   FUNC_f_HASH_<8hex>
 *   METH_m_OFSTRUCT_X_HASH_<8hex>
 *
 * The hash input is the canonical signature key
 * (see SemanticFunctionType.signatureKey()): declared parameter
 * types in order, then the return type, each spelled exactly as
 * the type is written in source.
 *
 * The algorithm is FNV-1a 32-bit over the UTF-8 bytes of the key:
 * deterministic across runs, JVMs, and platforms. Formatted as
 * lowercase hex, zero-padded to exactly 8 digits.
 */
final class Mangler {
    private static final int FNV_OFFSET_BASIS = 0x811C9DC5;
    private static final int FNV_PRIME = 0x01000193;

    private Mangler() {
    }

    static String hashHex(String key) {
        if (key == null) {
            throw new IllegalStateException(
                    "Internal compiler error: cannot hash null signature key"
            );
        }

        int hash = FNV_OFFSET_BASIS;

        byte[] bytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        for (byte b : bytes) {
            hash ^= (b & 0xFF);
            hash *= FNV_PRIME;
        }

        return String.format("%08x", hash);
    }
}
