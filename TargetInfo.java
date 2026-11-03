final class TargetInfo {

    static final String OS_LINUX = "linux";
    static final String OS_WINDOWS = "windows";
    static final String OS_MACOS = "macos";

    static final String ARCH_X86_64 = "x86_64";
    static final String ARCH_ARM64 = "arm64";

    final String os;
    final String arch;

    private TargetInfo(String os, String arch) {
        this.os = os;
        this.arch = arch;
    }

    String canonical() {
        return os + "-" + arch;
    }

    boolean isLinux() {
        return OS_LINUX.equals(os);
    }

    boolean isWindows() {
        return OS_WINDOWS.equals(os);
    }

    boolean isMacos() {
        return OS_MACOS.equals(os);
    }

    boolean isX86_64() {
        return ARCH_X86_64.equals(arch);
    }

    boolean isArm64() {
        return ARCH_ARM64.equals(arch);
    }

    static TargetInfo parse(String spec) {
        String[] parts = spec.split("-", -1);

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Invalid --target '" + spec + "': expected '<os>-<arch>'"
                            + " with os in {linux, windows, macos} and arch in"
                            + " {x86_64, arm64} (aliases: x64, amd64, aarch64)"
            );
        }

        String os = normalizeOs(parts[0]);
        String arch = normalizeArch(parts[1]);

        if (os == null || arch == null) {
            throw new IllegalArgumentException(
                    "Invalid --target '" + spec + "': expected '<os>-<arch>'"
                            + " with os in {linux, windows, macos} and arch in"
                            + " {x86_64, arm64} (aliases: x64, amd64, aarch64)"
            );
        }

        return new TargetInfo(os, arch);
    }

    private static String normalizeOs(String raw) {
        if (raw == null) {
            return null;
        }

        return switch (raw.toLowerCase()) {
            case "linux" -> OS_LINUX;
            case "windows" -> OS_WINDOWS;
            case "macos", "darwin" -> OS_MACOS;
            default -> null;
        };
    }

    private static String normalizeArch(String raw) {
        if (raw == null) {
            return null;
        }

        return switch (raw.toLowerCase()) {
            case "x86_64", "x64", "amd64" -> ARCH_X86_64;
            case "arm64", "aarch64" -> ARCH_ARM64;
            default -> null;
        };
    }

    @Override
    public String toString() {
        return canonical();
    }
}
