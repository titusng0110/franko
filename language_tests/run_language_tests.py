#!/usr/bin/env python3
"""Franko language test suite.

Replaces language_tests/run_language_tests.sh and
language_tests/run_language_tests.ps1; the same command works on every
platform:

    uv run python language_tests/run_language_tests.py

Runs the full language test suite:

  language_tests/failure/<family>/<case>.fr
    Must be REJECTED by the Franko compiler (non-zero exit).
    Optional <case>.errors: every non-empty line (lines starting with '#'
    are comments) must appear as a substring of the compiler output,
    pinning the expected diagnostics.
    Optional <case>.flags: every non-empty, non-comment line is
    word-split and appended to the compiler invocation.
    Helper files may live deeper (language_tests/failure/<family>/lib/);
    they are imported by a fixture and are not run standalone.

  language_tests/success/<concept>/main.fr
    Must compile, run, and produce stdout matching main.expected.
    Optional main.warnings: every non-empty line must appear as a
    substring of the compiler output, pinning expected warnings.
    Optional main.forbidden: every non-empty line must NOT appear as a
    substring of the compiler output, pinning warnings that must not
    be emitted.
    Optional main.flags: extra compiler flags, same format as above.
    Optional main.args: each line is one command-line argument passed
    verbatim to the binary (empty lines pass empty-string arguments; a
    trailing newline does not add an argument).

  language_tests/runtime/<family>/<case>.fr
    Must compile, but the binary must EXIT NON-ZERO when run. Used for
    documented runtime aborts such as uninitialized dynamic array access.
    Runtime messages are intentionally not pinned: they may change
    without breaking the suite.

Success golden outputs must be deterministic. Never put values whose
rendering varies across runs or platforms (such as live pointers) in a
.expected file; cover those through the runtime category, which is judged
by exit status only.

Generated artifacts (.cpp, .bin, .actual, compiler logs) are removed after
a passing case and kept for inspection after a failure.

Every build-backed case runs twice: once in ASan/system-allocator mode
(Franko without -O, C++ -O1 + AddressSanitizer) and once in optimized
mode (Franko -O, C++ -O3 + jemalloc). The optimized build keeps the
historical artifact names; the ASan build uses .asan-suffixed ones so
the modes never share failure evidence.
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))

from franko_tools import (
    INCLUDE_DIR,
    JAVA_HINT,
    ASAN_OPTIMIZE,
    BUILD_MODES,
    Results,
    ToolError,
    check_no_substrings,
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
    read_text,
    relative,
    remove_artifacts,
    require_toolchain,
    run,
    run_test_binary,
)


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
    for src in sorted(ROOT.glob("language_tests/failure/*/*.fr")):
        base = src.with_suffix("")
        errors = Path(f"{base}.errors")

        print(f"=== {relative(src)} ===")

        extra_flags = read_flags(Path(f"{base}.flags"))
        for mode in BUILD_MODES:
            out_cpp = mode_artifact(Path(f"{base}.cpp"), mode)
            log = mode_artifact(Path(f"{base}.compile.log"), mode)
            label = f"{relative(src)} [{mode}]"

            accepted = run(
                [java, "-cp", franko_classpath(), "Main", src, "-o", out_cpp,
                 *extra_flags, *java_mode_flags(mode)],
                log_file=log, echo=False, check=False,
            )

            if accepted == 0:
                print("---- compiler output (accepted a failure program): ----")
                dump_file(log)
                remove_artifacts(out_cpp, log)
                results.fail_case(label)
                continue

            if errors.is_file() and not check_substrings(errors, read_text(log)):
                print("---- compiler output (missing expected diagnostics): ----")
                dump_file(log)
                results.fail_case(f"{label} (missing expected diagnostics)")
                continue

            remove_artifacts(log, out_cpp)
            results.pass_case(label)

    # ------------------------------------------------------------
    # Success cases: compile, run, compare stdout with main.expected.
    # Each case builds twice: ASan/system-allocator mode and -O/jemalloc
    # mode. "opt" keeps the historical artifact names; "asan" uses
    # .asan-suffixed ones so failure evidence never clobbers.
    # ------------------------------------------------------------
    for src in sorted(ROOT.glob("language_tests/success/*/main.fr")):
        base = src.with_suffix("")
        expected = Path(f"{base}.expected")
        warnings = Path(f"{base}.warnings")
        forbidden = Path(f"{base}.forbidden")

        print(f"=== {relative(src)} ===")

        extra_flags = read_flags(Path(f"{base}.flags"))
        run_args = read_run_args(Path(f"{base}.args"))

        for mode in BUILD_MODES:
            out_cpp = mode_artifact(Path(f"{base}.cpp"), mode)
            out_bin = mode_artifact(Path(f"{base}.bin{executable_suffix()}"), mode)
            actual = mode_artifact(Path(f"{base}.actual"), mode)
            log = mode_artifact(Path(f"{base}.compile.log"), mode)
            label = f"{relative(src)} [{mode}]"
            optimize = mode == "opt"

            failed = run(
                [java, "-cp", franko_classpath(), "Main", src, "-o", out_cpp,
                 *extra_flags, *java_mode_flags(mode)],
                log_file=log, echo=False, check=False,
            )
            if failed != 0:
                print("---- compiler output: ----")
                dump_file(log)
                results.fail_case(f"{label} (Franko compile failed)")
                continue

            if warnings.is_file() and not check_substrings(warnings, read_text(log)):
                print("---- compiler output (missing expected warnings): ----")
                dump_file(log)
                results.fail_case(f"{label} (missing expected warnings)")
                continue

            if forbidden.is_file() and not check_no_substrings(forbidden, read_text(log)):
                print("---- compiler output (emitted forbidden warnings): ----")
                dump_file(log)
                results.fail_case(f"{label} (emitted forbidden warnings)")
                continue

            failed = run(
                [cxx, *cxx_compile_flags("full", optimize="3" if optimize else ASAN_OPTIMIZE,
                                          sanitize=not optimize),
                 f"-I{INCLUDE_DIR}", *([f"-I{include}"] if optimize else []),
                 out_cpp, *([jemalloc_lib] if optimize else []),
                 *link_flags(sanitize=not optimize), "-o", out_bin],
                log_file=log, echo=False, check=False,
            )
            if failed != 0:
                print("---- g++ output: ----")
                dump_file(log)
                results.fail_case(f"{label} (C++ compile failed)")
                continue

            if out_bin.stat().st_size == 0:
                results.fail_case(f"{label} (linked binary is empty, 0 bytes)")
                continue

            exit_code = run_test_binary(out_bin, run_args, None, actual)
            if exit_code != 0:
                print(f"---- runtime output (exit code {exit_code}): ----")
                dump_file(actual)
                results.fail_case(f"{label} (binary exited non-zero)")
                continue

            if not compare_output(actual, expected):
                results.fail_case(f"{label} (output mismatch)")
                continue

            remove_artifacts(out_cpp, out_bin, log, actual)
            results.pass_case(label)

    # ------------------------------------------------------------
    # Runtime cases: the program compiles but must abort at run time,
    # in both modes.
    # ------------------------------------------------------------
    for src in sorted(ROOT.glob("language_tests/runtime/*/*.fr")):
        base = src.with_suffix("")

        print(f"=== {relative(src)} ===")

        extra_flags = read_flags(Path(f"{base}.flags"))

        for mode in BUILD_MODES:
            out_cpp = mode_artifact(Path(f"{base}.cpp"), mode)
            out_bin = mode_artifact(Path(f"{base}.bin{executable_suffix()}"), mode)
            actual = mode_artifact(Path(f"{base}.actual"), mode)
            log = mode_artifact(Path(f"{base}.compile.log"), mode)
            label = f"{relative(src)} [{mode}]"
            optimize = mode == "opt"

            failed = run(
                [java, "-cp", franko_classpath(), "Main", src, "-o", out_cpp,
                 *extra_flags, *java_mode_flags(mode)],
                log_file=log, echo=False, check=False,
            )
            if failed != 0:
                print("---- compiler output: ----")
                dump_file(log)
                results.fail_case(f"{label} (Franko compile failed; runtime cases must compile)")
                continue

            failed = run(
                [cxx, *cxx_compile_flags("none", optimize="3" if optimize else ASAN_OPTIMIZE,
                                          sanitize=not optimize),
                 f"-I{INCLUDE_DIR}", *([f"-I{include}"] if optimize else []),
                 out_cpp, *([jemalloc_lib] if optimize else []),
                 *link_flags(sanitize=not optimize), "-o", out_bin],
                log_file=log, echo=False, check=False,
            )
            if failed != 0:
                print("---- g++ output: ----")
                dump_file(log)
                results.fail_case(f"{label} (C++ compile failed)")
                continue

            if out_bin.stat().st_size == 0:
                results.fail_case(f"{label} (linked binary is empty, 0 bytes)")
                continue

            # The abort itself is the assertion: any non-zero exit counts,
            # including the negative code a signal produces.
            exit_code = run_test_binary(out_bin, [], None, actual)
            if exit_code == 0:
                print("---- program output (expected a runtime failure, got exit 0): ----")
                dump_file(actual)
                remove_artifacts(out_cpp, out_bin, log, actual)
                results.fail_case(f"{label} (expected a runtime failure, exit 0)")
                continue

            remove_artifacts(out_cpp, out_bin, log, actual)
            results.pass_case(label)

    return results.finish("✅ All language tests passed.")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ToolError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
