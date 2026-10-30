"""Shared helpers for Franko's build and test scripts.

This module is the single implementation of behaviour that used to be
duplicated (and had already drifted apart) in the Linux shell scripts and
the Windows PowerShell scripts:

* host target detection and bundled-jemalloc lookup,
* tool discovery (``java``, ``javac``, ``g++``/``clang++``),
* running subprocesses with a printable command line,
* reading per-case fixture files (``.flags`` / ``.args`` / ``.stdin`` / ...),
* newline-safe comparison of actual vs expected output,
* running a test binary and capturing its output.

Everything works the same way on Linux, macOS and Windows. All text is read
with universal newlines and all comparisons normalise newlines, so a CRLF
checkout can never fail a test.

The scripts are run through uv:

    uv run python update_toolchain.py
"""

from __future__ import annotations

import contextlib
import difflib
import os
import platform
import shlex
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path


def _force_utf8_stdio() -> None:
    """Write our own output as UTF-8 whatever the host locale says.

    Windows still gives pipes and files the locale code page (cp1252 on
    GitHub-hosted runners), which cannot encode "✅" - or any non-ASCII
    byte a test program emits - and would crash a run with
    UnicodeEncodeError. Replacement characters absorb anything unencodable
    instead of raising, and CI logs are UTF-8 anyway.
    """
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, OSError, ValueError):
            # Already closed, or a redirect target without reconfigure.
            pass


_force_utf8_stdio()

# ------------------------------------------------------------
# Repository layout
# ------------------------------------------------------------

ROOT = Path(__file__).resolve().parent.parent

ANTLR_JAR = ROOT / "lib" / "antlr-4.13.2-complete.jar"
BUILD_DIR = ROOT / "build"
GENERATED_DIR = BUILD_DIR / "generated"
CLASSES_DIR = BUILD_DIR / "classes"
MAIN_CLASS = CLASSES_DIR / "Main.class"
IMPORT_DIR = ROOT / "import"
INCLUDE_DIR = ROOT / "include"
GRAMMAR_FILE = ROOT / "Franko.g4"

IS_WINDOWS = sys.platform in ("win32", "msys", "cygwin")
PATH_SEPARATOR = ";" if IS_WINDOWS else ":"


class ToolError(Exception):
    """A fatal, user-facing build/test error (printed as "Error: ...")."""


def relative(path: Path | str) -> str:
    """Render PATH relative to the repository root when possible."""
    path = Path(path)
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


# ------------------------------------------------------------
# Host target
# ------------------------------------------------------------

def host_os() -> str:
    if IS_WINDOWS:
        return "windows"
    if sys.platform == "darwin":
        return "macos"
    if sys.platform.startswith("linux"):
        return "linux"
    raise ToolError(
        f"unsupported host OS: {sys.platform} (expected linux, windows or macos)"
    )


def host_arch() -> str:
    machine = platform.machine().lower()
    if machine in ("x86_64", "amd64"):
        return "x86_64"
    if machine in ("arm64", "aarch64"):
        return "arm64"
    raise ToolError(
        f"unsupported host architecture: {machine!r} (expected x86_64 or arm64)"
    )


def host_target() -> str:
    """Canonical `--target` spec for the host, e.g. ``windows-x86_64``."""
    return f"{host_os()}-{host_arch()}"


def executable_suffix() -> str:
    return ".exe" if IS_WINDOWS else ""


def jemalloc_paths() -> tuple[Path, Path]:
    """Return (include dir, static library) for the host's jemalloc bundle."""
    bundle = ROOT / "third_party" / "jemalloc" / host_target()
    include = bundle / "include"
    library = bundle / "lib" / ("jemalloc.lib" if IS_WINDOWS else "libjemalloc.a")
    header = include / "jemalloc" / "jemalloc.h"

    if not header.is_file():
        raise ToolError(
            "bundled jemalloc header not found:\n"
            f"  {header}\n"
            "\nExpected layout:\n"
            f"  third_party/jemalloc/{host_target()}/include/jemalloc/jemalloc.h"
        )

    if not library.is_file():
        raise ToolError(
            "bundled jemalloc static library not found:\n"
            f"  {library}\n"
            "\nExpected layout:\n"
            f"  third_party/jemalloc/{host_target()}/lib/"
            + ("jemalloc.lib" if IS_WINDOWS else "libjemalloc.a")
        )

    return include, library


# Prebuilt jemalloc bundles are tracked as one small zip per target
# (produced by build-jemalloc.yml); the extracted tree is a build output.
JEMALLOC_ZIPS_DIR = ROOT / "third_party" / "artifacts"


def ensure_jemalloc_bundle() -> None:
    """Extract the host's prebuilt jemalloc bundle from its checked-in zip.

    Skips extraction when the header and static library are already in
    place. Produces exactly the layout jemalloc_paths() consumes:
    third_party/jemalloc/<target>/{include/jemalloc/jemalloc.h,
    lib/libjemalloc.a | lib/jemalloc.lib}.
    """
    bundle = ROOT / "third_party" / "jemalloc" / host_target()
    include = bundle / "include" / "jemalloc" / "jemalloc.h"
    library = bundle / "lib" / ("jemalloc.lib" if IS_WINDOWS else "libjemalloc.a")

    if include.is_file() and library.is_file():
        return

    archive = JEMALLOC_ZIPS_DIR / f"jemalloc-{host_target()}.zip"
    if not archive.is_file():
        raise ToolError(
            "prebuilt jemalloc bundle zip not found:\n"
            f"  {archive}\n"
            "\nThe zips are produced by the 'Build jemalloc static bundles'"
            " workflow; download jemalloc-<target>.zip from a green run into"
            " third_party/artifacts/."
        )

    print(f"extracting {relative(archive)} ...")
    with zipfile.ZipFile(archive) as zf:
        zf.extractall(bundle)

    if not include.is_file() or not library.is_file():
        raise ToolError(
            "jemalloc bundle zip did not contain the expected layout:\n"
            f"  {archive}"
        )
    print("jemalloc bundle ready.")


def cxx_compile_flags(
    warnings: str = "basic", optimize: str = "3", sanitize: bool = False
) -> list[str]:
    """Common C++ compile flags for Franko output and test programs.

    WARNINGS is one of:
      "full"  -Wall -Wextra -Wpedantic -Wshadow (test suite builds),
      "basic" -Wall -Wextra -Wpedantic          (compile.py, matching the
                                                 old compile_* scripts),
      "none"  no warning flags                  (test runtime builds).

    OPTIMIZE is the -O level ("3" for optimized builds, ASAN_OPTIMIZE
    for sanitizer builds). SANITIZE adds AddressSanitizer
    (-fsanitize=address plus debug info) for debug builds; the
    matching link flags come from link_flags(sanitize=True), since the
    suites and compile.py build and link in a single driver invocation.
    """
    flags = [f"-O{optimize}", "-std=c++14"]
    if warnings == "full":
        flags += ["-Wall", "-Wextra", "-Wpedantic", "-Wshadow"]
    elif warnings == "basic":
        flags += ["-Wall", "-Wextra", "-Wpedantic"]
    elif warnings != "none":
        raise ValueError(f"unknown warning level: {warnings!r}")
    if sanitize:
        flags += ["-fsanitize=address", "-g", "-fno-omit-frame-pointer"]
    return flags


# ------------------------------------------------------------
# Dual-mode (ASan / optimized) builds
# ------------------------------------------------------------

BUILD_MODES = ("opt",) if IS_WINDOWS else ("asan", "opt")
"""Build modes used by compile.py and the test runners.

"asan" is the default debug mode: the Franko compiler runs without -O
(selecting the system-allocator runtime header) and the C++ step uses
-O1 + AddressSanitizer with no jemalloc. "opt" is the optimized mode:
the compiler runs with -O (jemalloc header) and the C++ step uses -O3
with the bundled jemalloc.

Windows does not support AddressSanitizer, so only "opt" runs there:
compile.py's default build is "opt" on Windows, and the test runners
iterate just ("opt",) and never exercise the "asan" mode.
"""


ASAN_OPTIMIZE = "1"
"""-O level for the default ASan/debug builds.

-O1 (not -O0) keeps full sanitizer coverage while recovering most of
-O0's overhead; -g -fno-omit-frame-pointer (added by cxx_compile_flags
with sanitize=True) keep the reports readable.
"""


def java_mode_flags(mode: str) -> list[str]:
    """Extra Franko-compiler flags for MODE: ["-O"] for "opt", [] for "asan"."""
    if mode == "opt":
        return ["-O"]
    if mode == "asan":
        return []
    raise ValueError(f"unknown build mode: {mode!r}")


def mode_artifact(path: Path, mode: str) -> Path:
    """Map a status-quo artifact PATH to its per-mode path.

    "opt" keeps the path unchanged; "asan" inserts ".asan" after the
    filename stem, before all suffixes (main.cpp -> main.asan.cpp,
    main.compile.log -> main.asan.compile.log), so the two modes never
    share - and can never clobber - build artifacts or failure evidence.
    Splitting at the first dot (not the last) keeps the infix position
    identical on every platform: main.bin -> main.asan.bin on POSIX and
    main.asan.bin.exe (not main.bin.asan.exe) on Windows.
    """
    if mode == "opt":
        return path
    if mode == "asan":
        head, dot, tail = path.name.partition(".")
        if not dot:
            return path.with_name(f"{path.name}.asan")
        return path.with_name(f"{head}.asan.{tail}")
    raise ValueError(f"unknown build mode: {mode!r}")


def link_flags(sanitize: bool = False) -> list[str]:
    """Link flags for a native binary, per host OS.

    Linux keeps pthread/dl and macOS keeps pthread in every mode: the
    statically linked jemalloc references both symbol groups on Linux
    (they only matter for glibc < 2.34, where libdl and libpthread are
    not merged into libc yet; dl lives in libSystem on macOS), and the
    AddressSanitizer runtime wants pthread too, so sanitize builds keep
    the same thread flags even though they link no jemalloc. MinGW needs
    neither. The static libstdc++/libgcc pair (Linux/Windows only) keeps
    binaries independent of the system's GCC runtimes; Apple's clang
    has no libstdc++ to link.

    SANITIZE adds -fsanitize=address (required at link time as well as
    compile time). It also drops -s: stripping a sanitizer build would
    discard the symbols its reports need. AddressSanitizer is always
    linked dynamically (no -static-libasan): the flag does not exist on
    every supported toolchain (e.g. Windows MinGW-w64), and the shared
    runtime is found automatically on all three platforms.
    """
    flags: list[str] = []
    system = host_os()
    if sanitize:
        flags.append("-fsanitize=address")
    if system in ("linux", "macos"):
        flags.append("-pthread")
    if system == "linux":
        flags.append("-ldl")
    if system != "macos":
        flags += ["-static-libstdc++", "-static-libgcc"]
        # Apple's clang treats -s as obsolete; GNU ld accepts it.
        # Never strip sanitizer builds (see docstring).
        if not sanitize:
            flags.append("-s")
    return flags


# ------------------------------------------------------------
# Tool discovery
# ------------------------------------------------------------

JAVA_HINT = "Install a JDK (Java 25) and make sure java/javac are on PATH."
CXX_HINT = (
    "Install a C++14 compiler (g++ or clang++; MinGW-w64 g++ on Windows, "
    "with its bin directory such as C:\\msys64\\ucrt64\\bin on PATH) and "
    "make sure the compiler is on PATH."
)


def find_tool(name: str, hint: str = "") -> Path:
    """Locate NAME on PATH (NAME may be a full path too, as shutil.which
    checks any name containing a directory component directly)."""
    found = shutil.which(name)
    if found is None:
        message = f"required tool not found on PATH: {name}"
        if hint:
            message += f"\n{hint}"
        raise ToolError(message)
    return Path(found)


def find_cxx() -> Path:
    """Locate the host C++ compiler: $CXX if set, else g++, else clang++.

    The compiler must be a GCC-compatible driver (MinGW-w64 g++, Apple
    clang, Linux clang): the compile/link flags are GCC-style. On Windows
    the compiler's bin directory must be on PATH for its own pieces (and
    the runtime DLLs of test binaries, e.g. libwinpthread-1.dll) to be
    found - which it is, since that is where we found the compiler.
    """
    override = os.environ.get("CXX")
    if override:
        return find_tool(override, CXX_HINT)
    for candidate in ("g++", "clang++"):
        found = shutil.which(candidate)
        if found is not None:
            return Path(found)
    raise ToolError(f"required tool not found on PATH: g++ or clang++\n{CXX_HINT}")


def format_command(cmd: list[str]) -> str:
    if IS_WINDOWS:
        return subprocess.list2cmdline([str(part) for part in cmd])
    return shlex.join(str(part) for part in cmd)


def run(
    cmd: list,
    *,
    cwd: Path = ROOT,
    log_file: Path | None = None,
    echo: bool = True,
    check: bool = True,
) -> int:
    """Run a command and return its exit code.

    With LOG_FILE the process output is captured there instead of being
    inherited; with ECHO the command line is printed first. When CHECK is
    set a non-zero exit raises ToolError.
    """
    parts = [str(part) for part in cmd]

    if echo:
        print(format_command(parts))

    # The child inherits this process's stdout/stderr; flush our own buffer
    # first so the printed command line is not reordered against the child
    # output when stdout is a pipe.
    sys.stdout.flush()
    sys.stderr.flush()

    if log_file is None:
        completed = subprocess.run(parts, cwd=cwd, check=False)
    else:
        with log_file.open("wb") as handle:
            completed = subprocess.run(
                parts, cwd=cwd, stdout=handle,
                stderr=subprocess.STDOUT, check=False,
            )

    if check and completed.returncode != 0:
        raise ToolError(
            f"command failed with exit code {completed.returncode}: "
            f"{format_command(parts)}"
        )

    return completed.returncode


# ------------------------------------------------------------
# ANTLR / Java compiler toolchain
# ------------------------------------------------------------

def _base_classpath_entries() -> list[str]:
    entries = [".", str(ANTLR_JAR)]
    extra = os.environ.get("CLASSPATH")
    if extra:
        entries.append(extra)
    return entries


def antlr_classpath() -> str:
    """Classpath for running the ANTLR tool itself."""
    return PATH_SEPARATOR.join(_base_classpath_entries())


def franko_classpath() -> str:
    """Classpath for running the Franko compiler (Main)."""
    entries = [*_base_classpath_entries(), str(CLASSES_DIR), str(GENERATED_DIR)]
    return PATH_SEPARATOR.join(entries)


def _javac_classpath() -> str:
    return PATH_SEPARATOR.join([*_base_classpath_entries(), str(GENERATED_DIR)])


def build_toolchain(java_release: str | None = None) -> None:
    """Regenerate the ANTLR parser sources and rebuild the Java classes.

    Replaces update_linux-x86_64.sh / update_windows-x64.ps1. It runs the
    steps without prompting, so the same command works locally and in CI.

    JAVA_RELEASE defaults to $FRANKO_JAVA_RELEASE, else "25": the javac
    --release value for the compiler classes. CI passes 23 on
    windows-11-arm, whose preinstalled JDK is 23 (no aarch64 Temurin 25
    exists), and 25 everywhere else.
    """
    if java_release is None:
        java_release = os.environ.get("FRANKO_JAVA_RELEASE", "25")

    if not ANTLR_JAR.is_file():
        raise ToolError(f"ANTLR jar not found at {ANTLR_JAR}")

    java = find_tool("java", JAVA_HINT)
    javac = find_tool("javac", JAVA_HINT)

    print("[Step 1] clear generated and class output directories")
    shutil.rmtree(GENERATED_DIR, ignore_errors=True)
    shutil.rmtree(CLASSES_DIR, ignore_errors=True)
    GENERATED_DIR.mkdir(parents=True, exist_ok=True)
    CLASSES_DIR.mkdir(parents=True, exist_ok=True)
    print("[Step 1] Done.")

    print()
    print("[Step 2] generate ANTLR parser/visitor files")
    run([
        java, "-Xmx500M", "-cp", antlr_classpath(),
        "org.antlr.v4.Tool", "-visitor", "-o", GENERATED_DIR, GRAMMAR_FILE.name,
    ])
    print("[Step 2] Done.")

    sources = sorted(ROOT.glob("*.java")) + sorted(GENERATED_DIR.glob("*.java"))
    if not sources:
        raise ToolError(f"no Java source files found to compile in {ROOT}")

    print()
    print(f"[Step 3] compile Java compiler classes (--release {java_release})")
    run([
        javac, "--release", java_release, "-Xlint:all,-auxiliaryclass",
        "-cp", _javac_classpath(), "-d", CLASSES_DIR, *sources,
    ])
    print("[Step 3] Done.")

    if not MAIN_CLASS.is_file():
        raise ToolError(f"toolchain build did not produce {relative(MAIN_CLASS)}")

    print()
    print("✅ Compiler toolchain updated successfully.")


def require_toolchain() -> None:
    """Fail with a clear hint when the Java compiler classes are missing."""
    if MAIN_CLASS.is_file():
        return
    raise ToolError(
        f"compiler toolchain not built: {relative(MAIN_CLASS)}\n"
        "Run `uv run python update_toolchain.py` first."
    )


def ensure_toolchain() -> None:
    """Build the toolchain when it is missing (used by compile.py)."""
    if MAIN_CLASS.is_file():
        return
    print(f"Build not found ({relative(MAIN_CLASS)} missing). Running toolchain update...")
    print()
    build_toolchain()
    print()


# ------------------------------------------------------------
# Fixture files
# ------------------------------------------------------------

def read_text(path: Path) -> str:
    """Read a text file with universal newlines (CRLF and lone CR become LF).

    utf-8-sig also swallows a byte-order mark, so a fixture saved by a
    Windows editor reads the same as one saved on Linux (PowerShell's
    ReadAllText stripped BOMs the same way).
    """
    with path.open(
        "r", encoding="utf-8-sig", errors="replace", newline=None
    ) as handle:
        return handle.read()


def _read_lines(path: Path) -> list[str]:
    if not path.is_file():
        return []
    return read_text(path).split("\n")


def read_flags(path: Path) -> list[str]:
    """Read an optional per-case `.flags` file.

    Each non-empty, non-comment line is split on whitespace (one line may
    hold several flags) and appended to the compiler invocation.
    """
    flags: list[str] = []
    for line in _read_lines(path):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        flags.extend(stripped.split())
    return flags


def read_run_args(path: Path) -> list[str]:
    """Read an optional `.args` file.

    Each line is one argument passed verbatim to the binary; empty lines
    pass empty-string arguments; a trailing newline does not add an
    argument; a missing or empty file yields no arguments.
    """
    if not path.is_file():
        return []
    text = read_text(path)
    if not text:
        return []
    lines = text.split("\n")
    if lines and lines[-1] == "":
        lines.pop()
    return lines


def read_stdin_bytes(path: Path) -> bytes | None:
    """Read an optional `.stdin` file with carriage returns stripped.

    Stripping \r means a CRLF checkout behaves exactly like an LF one.
    Returns None when the file does not exist.
    """
    if not path.is_file():
        return None
    return path.read_bytes().replace(b"\r", b"")


def remove_artifacts(*paths: Path) -> None:
    for path in paths:
        # Mirrors the old `rm -f` / Remove-Item -ErrorAction SilentlyContinue:
        # a locked file on Windows must not fail an otherwise passing case.
        with contextlib.suppress(OSError):
            path.unlink()


def dump_file(path: Path) -> None:
    """Print a captured file verbatim (used when a case fails)."""
    if not path.is_file():
        return
    text = read_text(path)
    sys.stdout.write(text)
    if text and not text.endswith("\n"):
        sys.stdout.write("\n")


# ------------------------------------------------------------
# Comparisons
# ------------------------------------------------------------

def check_substrings(expected_path: Path, observed: str) -> bool:
    """Check that every non-empty, non-comment line of EXPECTED occurs in
    OBSERVED (used for expected diagnostics and expected warnings).

    Both sides are normalised to '/' so the checked-in `.errors`/`.warnings`
    files can pin paths in POSIX form on every platform, while compilers on
    Windows keep printing backslashes.
    """
    haystack = observed.replace("\\", "/")
    ok = True

    for line in _read_lines(expected_path):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if stripped.replace("\\", "/") not in haystack:
            print(f"expected text not found in output: {stripped}")
            ok = False

    return ok


def check_no_substrings(forbidden_path: Path, observed: str) -> bool:
    """Check that no non-empty, non-comment line of FORBIDDEN occurs in
    OBSERVED (used for warnings that must not be emitted).

    Normalisation matches `check_substrings`.
    """
    haystack = observed.replace("\\", "/")
    ok = True

    for line in _read_lines(forbidden_path):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if stripped.replace("\\", "/") in haystack:
            print(f"forbidden text found in output: {stripped}")
            ok = False

    return ok


def compare_output(actual_path: Path, expected_path: Path) -> bool:
    """Compare two files after newline normalisation, printing a diff."""
    if not expected_path.is_file():
        print(f"expected file not found: {relative(expected_path)}")
        return False

    actual = read_text(actual_path).split("\n")
    expected = read_text(expected_path).split("\n")

    if actual == expected:
        return True

    print(f"---- output mismatch: {relative(expected_path)} vs {relative(actual_path)} ----")
    diff_lines = list(difflib.unified_diff(
        expected,
        actual,
        fromfile=relative(expected_path),
        tofile=relative(actual_path),
        lineterm="",
    ))
    for line in diff_lines[:200]:
        print(line)
    if len(diff_lines) > 200:
        print("... (diff truncated)")
    return False


def run_test_binary(
    binary: Path,
    args: list[str],
    stdin_bytes: bytes | None,
    actual_path: Path,
) -> int:
    """Run a test binary, merging stdout+stderr into ACTUAL_PATH.

    The process exit code is returned; a binary killed by a signal yields a
    negative code, which callers treat like any other non-zero exit. stdin
    is always provided explicitly (empty when no `.stdin` fixture exists)
    so no case can ever block waiting on a terminal.
    """
    with actual_path.open("wb") as handle:
        completed = subprocess.run(
            [str(binary), *args],
            cwd=ROOT,
            input=b"" if stdin_bytes is None else stdin_bytes,
            stdout=handle,
            stderr=subprocess.STDOUT,
            check=False,
        )
    return completed.returncode


# ------------------------------------------------------------
# Suite bookkeeping
# ------------------------------------------------------------

class Results:
    """Pass/fail/skip counters shared by the three test suites."""

    def __init__(self) -> None:
        self.passed = 0
        self.failed = 0
        self.skipped = 0

    def pass_case(self, message: str) -> None:
        self.passed += 1
        print(f"PASS: {message}")

    def fail_case(self, message: str) -> None:
        self.failed += 1
        print(f"FAIL: {message}")

    def skip_case(self, message: str) -> None:
        self.skipped += 1
        print(f"SKIP: {message}")

    def finish(self, success_message: str) -> int:
        """Print the summary and return the process exit code."""
        print()
        print(
            f"passed: {self.passed}  failed: {self.failed}  "
            f"skipped: {self.skipped}"
        )
        print()
        if self.failed:
            return 1
        print(success_message)
        return 0
