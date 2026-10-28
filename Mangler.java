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
