enum PtrPermission {
    OWNER("ptr"),
    MUTABLE("mview"),
    READ_ONLY("view");

    private final String spelling;

    PtrPermission(String spelling) {
        this.spelling = spelling;
    }

    String spelling() {
        return spelling;
    }

    boolean allows(PtrPermission other) {
        return this.ordinal() <= other.ordinal();
    }
}
