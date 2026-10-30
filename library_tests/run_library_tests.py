#!/usr/bin/env python3
"""Franko library test suite.

Replaces library_tests/run_library_tests.sh and
library_tests/run_library_tests.ps1; the same command works on every
platform:

    uv run python library_tests/run_library_tests.py

Tests the importable Franko standard-library modules in library_tests/
(tests/ covers the language itself and never imports library modules).

Layout:

  library_tests/<module>/<suite>/success/<case>/main.fr
    Must compile, run, and produce stdout matching main.expected.
    Optional main.warnings: every non-empty line must appear as a
    substring of the compiler output, pinning expected warnings.
    Optional main.flags: extra compiler flags, one per line (word-split
    like the spec suite).
    Optional main.args: each line is one command-line argument passed
    verbatim to the binary (empty lines pass empty-string arguments; a
    trailing newline does not add an argument).
    Optional main.stdin: bytes fed to the binary on stdin (carriage
    returns are stripped, so CRLF checkouts behave like LF ones).

  library_tests/<module>/<suite>/failure/<case>/main.fr
    Must be REJECTED by the Franko compiler (non-zero exit).
    Optional main.errors: every non-empty line (lines starting with '#'
    are comments) must appear as a substring of the compiler output,
    pinning the expected diagnostics.
    Optional main.flags: extra compiler flags, same format.

  library_tests/<module>/<suite>/runtime/<case>/main.fr
    Must compile, but the binary must EXIT NON-ZERO when run. Used for
    documented runtime aborts such as FFI buffer-bound panics. Runtime
    messages are intentionally not pinned: they may change without
    breaking the suite. Optional main.flags, main.args, and main.stdin as
    in the success category.

Platform suites:

  A suite directory named exactly `win32` runs on Windows only and is
  skipped everywhere else (the os win32 File API bridge is a compile-time
  #error off Windows). A suite directory named exactly `posix` runs on
  non-Windows platforms only and is skipped on Windows (the os posix
  bridge is a compile-time #error on Windows). Every other suite runs on
  all platforms.

Success golden outputs must be deterministic. Never put values whose
rendering varies across runs or platforms (such as live pointers) in a
.expected file; cover those through the runtime category, which is judged
by exit status only.

Generated artifacts (main.cpp, main.bin, main.actual, compiler logs) are
removed after a passing case and kept for inspection after a failure.

Every build-backed case runs twice: ASan/system-allocator mode (Franko
without -O, C++ -O0 + AddressSanitizer) and optimized mode (Franko -O,
C++ -O3 + jemalloc). The optimized build keeps the historical artifact
names; the ASan build uses .asan-suffixed ones so the modes never share
failure evidence.
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))

from franko_tools import (
    IMPORT_DIR,
    INCLUDE_DIR,
    IS_WINDOWS,
    JAVA_HINT,
    BUILD_MODES,
    Results,
    ToolError,
    check_substrings,
    compare_output,
    cxx_compile_flags,
    dump_file,
    executable_suffix,
    find_cxx,
    find_tool,
    franko_classpath,
    java_mode_flags,
    jemalloc_paths,
    link_flags,
    mode_artifact,
    read_flags,
    read_run_args,
    read_stdin_bytes,
    read_text,
    relative,
    remove_artifacts,
    require_toolchain,
    run,
    run_test_binary,
)


def suite_name(src: Path) -> str:
    """Return the suite directory of a case (library_tests/<mod>/<suite>/...)."""
    return src.relative_to(ROOT).parts[2]


def skip_platform_suite(src: Path, results: Results) -> bool:
    """Apply the win32/posix suite rules; True when the case is skipped."""
    suite = suite_name(src)

    if suite == "win32" and not IS_WINDOWS:
        results.skip_case(f"{relative(src)} (windows-only suite)")
        return True

    if suite == "posix" and IS_WINDOWS:
        results.skip_case(f"{relative(src)} (posix suite does not run on Windows)")
        return True

    return False


def case_paths(src: Path, mode: str = "opt") -> dict[str, Path]:
    """All artifact/fixture paths of a library test case.

    MODE selects the build artifacts: "opt" keeps the historical names,
    "asan" uses .asan-suffixed ones (main.asan.cpp, ...) so the two modes
    never share failure evidence. Fixture inputs (expected/errors/flags/
    args/stdin) are mode-independent.
    """
    base = src.with_suffix("")
    return {
        "base": base,
        "cpp": mode_artifact(Path(f"{base}.cpp"), mode),
        "bin": mode_artifact(Path(f"{base}.bin{executable_suffix()}"), mode),
        "actual": mode_artifact(Path(f"{base}.actual"), mode),
        "log": mode_artifact(Path(f"{base}.compile.log"), mode),
        "expected": Path(f"{base}.expected"),
        "errors": Path(f"{base}.errors"),
        "warnings": Path(f"{base}.warnings"),
        "flags": Path(f"{base}.flags"),
        "args": Path(f"{base}.args"),
        "stdin": Path(f"{base}.stdin"),
    }


def main() -> int:
    require_toolchain()
    java = find_tool("java", JAVA_HINT)
    cxx = find_cxx()
    include, jemalloc_lib = jemalloc_paths()

    results = Results()

    # ------------------------------------------------------------
    # Failure cases: the compiler must reject the program, in both modes
    # (Java-only; -O must not alter diagnostics).
    # ------------------------------------------------------------
    for src in sorted(ROOT.glob("library_tests/*/*/failure/*/main.fr")):
        print(f"=== {relative(src)} ===")

        if skip_platform_suite(src, results):
            continue

        for mode in BUILD_MODES:
            paths = case_paths(src, mode)
            label = f"{relative(src)} [{mode}]"

            extra_flags = read_flags(paths["flags"])
            accepted = run(
                [java, "-cp", franko_classpath(), "Main", src, "-o", paths["cpp"],
                 "-p", IMPORT_DIR, *extra_flags, *java_mode_flags(mode)],
                log_file=paths["log"], echo=False, check=False,
            )

            if accepted == 0:
                print("---- compiler output (accepted a failure program): ----")
                dump_file(paths["log"])
                remove_artifacts(paths["cpp"], paths["log"])
                results.fail_case(label)
                continue

            if paths["errors"].is_file() and not check_substrings(
                paths["errors"], read_text(paths["log"])
            ):
                print("---- compiler output (missing expected diagnostics): ----")
                dump_file(paths["log"])
                results.fail_case(f"{label} (missing expected diagnostics)")
                continue

            remove_artifacts(paths["log"], paths["cpp"])
            results.pass_case(label)

    # ------------------------------------------------------------
    # Success cases: compile, run, compare stdout with main.expected.
    # ------------------------------------------------------------
    for src in sorted(ROOT.glob("library_tests/*/*/success/*/main.fr")):
        print(f"=== {relative(src)} ===")

        if skip_platform_suite(src, results):
            continue

        for mode in BUILD_MODES:
            paths = case_paths(src, mode)
            label = f"{relative(src)} [{mode}]"
            optimize = mode == "opt"

            extra_flags = read_flags(paths["flags"])
            run_args = read_run_args(paths["args"])
            stdin_bytes = read_stdin_bytes(paths["stdin"])

            failed = run(
                [java, "-cp", franko_classpath(), "Main", src, "-o", paths["cpp"],
                 "-p", IMPORT_DIR, *extra_flags, *java_mode_flags(mode)],
                log_file=paths["log"], echo=False, check=False,
            )
            if failed != 0:
                print("---- compiler output: ----")
                dump_file(paths["log"])
                results.fail_case(f"{label} (Franko compile failed)")
                continue

            if paths["warnings"].is_file() and not check_substrings(
                paths["warnings"], read_text(paths["log"])
            ):
                print("---- compiler output (missing expected warnings): ----")
                dump_file(paths["log"])
                results.fail_case(f"{label} (missing expected warnings)")
                continue

            failed = run(
                [cxx, *cxx_compile_flags("full", optimize="3" if optimize else "0",
                                          sanitize=not optimize),
                 f"-I{INCLUDE_DIR}", *([f"-I{include}"] if optimize else []),
                 paths["cpp"], *([jemalloc_lib] if optimize else []),
                 *link_flags(sanitize=not optimize), "-o", paths["bin"]],
                log_file=paths["log"], echo=False, check=False,
            )
            if failed != 0:
                print("---- g++ output: ----")
                dump_file(paths["log"])
                results.fail_case(f"{label} (C++ compile failed)")
                continue

            if paths["bin"].stat().st_size == 0:
                results.fail_case(f"{label} (linked binary is empty, 0 bytes)")
                continue

            exit_code = run_test_binary(paths["bin"], run_args, stdin_bytes, paths["actual"])
            if exit_code != 0:
                print(f"---- runtime output (exit code {exit_code}): ----")
                dump_file(paths["actual"])
                results.fail_case(f"{label} (binary exited non-zero)")
                continue

            if not compare_output(paths["actual"], paths["expected"]):
                results.fail_case(f"{label} (output mismatch)")
                continue

            remove_artifacts(paths["cpp"], paths["bin"], paths["log"], paths["actual"])
            results.pass_case(label)

    # ------------------------------------------------------------
    # Runtime cases: the program compiles but must abort at run time.
    # ------------------------------------------------------------
    for src in sorted(ROOT.glob("library_tests/*/*/runtime/*/main.fr")):
        print(f"=== {relative(src)} ===")

        if skip_platform_suite(src, results):
            continue

        for mode in BUILD_MODES:
            paths = case_paths(src, mode)
            label = f"{relative(src)} [{mode}]"
            optimize = mode == "opt"

            extra_flags = read_flags(paths["flags"])
            run_args = read_run_args(paths["args"])
            stdin_bytes = read_stdin_bytes(paths["stdin"])

            failed = run(
                [java, "-cp", franko_classpath(), "Main", src, "-o", paths["cpp"],
                 "-p", IMPORT_DIR, *extra_flags, *java_mode_flags(mode)],
                log_file=paths["log"], echo=False, check=False,
            )
            if failed != 0:
                print("---- compiler output: ----")
                dump_file(paths["log"])
                results.fail_case(
                    f"{label} (Franko compile failed; runtime cases must compile)"
                )
                continue

            failed = run(
                [cxx, *cxx_compile_flags("none", optimize="3" if optimize else "0",
                                          sanitize=not optimize),
                 f"-I{INCLUDE_DIR}", *([f"-I{include}"] if optimize else []),
                 paths["cpp"], *([jemalloc_lib] if optimize else []),
                 *link_flags(sanitize=not optimize), "-o", paths["bin"]],
                log_file=paths["log"], echo=False, check=False,
            )
            if failed != 0:
                print("---- g++ output: ----")
                dump_file(paths["log"])
                results.fail_case(f"{label} (C++ compile failed)")
                continue

            if paths["bin"].stat().st_size == 0:
                results.fail_case(f"{label} (linked binary is empty, 0 bytes)")
                continue

            # The abort itself is the assertion: any non-zero exit counts,
            # including the negative code a signal produces.
            exit_code = run_test_binary(paths["bin"], run_args, stdin_bytes, paths["actual"])
            if exit_code == 0:
                print("---- program output (expected a runtime failure, got exit 0): ----")
                dump_file(paths["actual"])
                remove_artifacts(
                    paths["cpp"], paths["bin"], paths["log"], paths["actual"]
                )
                results.fail_case(f"{label} (expected a runtime failure, exit 0)")
                continue

            remove_artifacts(paths["cpp"], paths["bin"], paths["log"], paths["actual"])
            results.pass_case(label)

    return results.finish("✅ All library tests passed.")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ToolError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
