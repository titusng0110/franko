import java.math.BigInteger;

/**
 * ============================================================================
 * COMPILATION TARGET (OS + ARCHITECTURE)
 * ============================================================================
 *
 * PURPOSE
 *
 * TargetInfo owns the Java compiler's notion of *which machine the program
 * is being compiled for*. It is the single source of truth behind the
 * `os::IS_*` target constants in `import/franko/os.fr`:
 *
 *   - `IS_LINUX` / `IS_WINDOWS` / `IS_MACOS`
 *   - `IS_X86_64` / `IS_ARM64`
 *
 * The target comes from the `--target <os>-<arch>` CLI flag when given,
 * and falls back to auto-detection from the JVM host
 * (`os.name` / `os.arch`) otherwise.
 *
 * Supported canonical targets:
 *
 *   linux-x86_64, linux-arm64,
 *   windows-x86_64, windows-arm64,
 *   macos-x86_64, macos-arm64
 *
 * Arch aliases `x64`, `amd64`, and `aarch64` are accepted and normalized.
 */
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

    /**
     * Canonical target spelling, e.g. "linux-x86_64".
     */
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

    /**
     * The 0/1 value of one `os::IS_*` constant name for this target.
     */
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

    // ============================================================
    // Parsing and Host Detection
    // ============================================================

    /**
     * Parses a `--target` spec such as "linux-x86_64" or "windows-x64".
     *
     * Throws IllegalArgumentException with the supported values on any
     * malformed or unknown spec.
     */
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

    /**
     * Detects the compilation target from the JVM host.
     *
     * Throws IllegalStateException naming `--target` when the host OS or
     * architecture is not recognized.
     */
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
