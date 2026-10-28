import java.math.BigInteger;

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

    BigInteger constantFor(String name) {
        return switch (name) {
            case "IS_LINUX" -> isLinux() ? BigInteger.ONE : BigInteger.ZERO;
            case "IS_WINDOWS" -> isWindows() ? BigInteger.ONE : BigInteger.ZERO;
            case "IS_MACOS" -> isMacos() ? BigInteger.ONE : BigInteger.ZERO;
            case "IS_X86_64" -> isX86_64() ? BigInteger.ONE : BigInteger.ZERO;
            case "IS_ARM64" -> isArm64() ? BigInteger.ONE : BigInteger.ZERO;
            default -> throw new IllegalArgumentException(
                    "Unknown target constant '" + name + "'"
            );
        };
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

    static TargetInfo detectHost() {
        String osName = System.getProperty("os.name", "");
        String osArch = System.getProperty("os.arch", "");

        String os = normalizeHostOs(osName);
        String arch = normalizeArch(osArch);

        if (os == null) {
            throw new IllegalStateException(
                    "Cannot determine target OS from os.name '" + osName + "'"
                            + ": pass --target <os>-<arch> explicitly"
                            + " (os in {linux, windows, macos})"
            );
        }

        if (arch == null) {
            throw new IllegalStateException(
                    "Cannot determine target architecture from os.arch '"
                            + osArch + "': pass --target <os>-<arch> explicitly"
                            + " (arch in {x86_64, arm64})"
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

    private static String normalizeHostOs(String osName) {
        if (osName == null) {
            return null;
        }

        String lower = osName.toLowerCase();

        if (lower.startsWith("windows")) {
            return OS_WINDOWS;
        }

        if (lower.startsWith("linux")) {
            return OS_LINUX;
        }

        if (lower.startsWith("mac")) {
            return OS_MACOS;
        }

        return null;
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
