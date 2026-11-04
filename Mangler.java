final class Mangler {

    private static final long FNV_OFFSET_BASIS = 0x811C9DC5L;
    private static final long FNV_PRIME_64 = 0x100000001b3L;

    private Mangler() {
    }

    static String hashHex(String key) {
        return String.format("%08x", (int) hash64(key));
    }

    static String hashHex64(String key) {
        return String.format("%016x", hash64(key));
    }

    private static long hash64(String key) {
        if (key == null) {
            throw new IllegalStateException(
                    "Internal compiler error: cannot hash null signature key"
            );
        }

        long hash = FNV_OFFSET_BASIS;

        byte[] bytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        for (byte b : bytes) {
            hash ^= (b & 0xFFL);
            hash *= FNV_PRIME_64;
        }

        return hash;
    }
}
