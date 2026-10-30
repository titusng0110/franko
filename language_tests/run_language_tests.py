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

Every build-backed case runs in each active build mode (see
franko_tools.active_build_modes): ASan/system-allocator mode (Franko
without -O, C++ -O1 + AddressSanitizer) and optimized mode (Franko -O,
C++ -O3 + jemalloc) on Linux/macOS; optimized mode only on Windows,
where AddressSanitizer is unsupported. Both stages build through
compile.franko_compile / compile.native_compile, shared with
compile.py and the other suites. The optimized build keeps the
historical artifact names; the ASan build uses .asan-suffixed ones so
the modes never share failure evidence.
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT))

from compile import franko_compile, native_compile
from franko_tools import (
    Results,
    ToolError,
    active_build_modes,
    check_no_substrings,
    check_substrings,
    compare_output,
    dump_file,
    executable_suffix,
    mode_artifact,
    read_flags,
    read_run_args,
    read_text,
    relative,
    remove_artifacts,
    require_toolchain,
    run_test_binary,
)


def main() -> int:
    require_toolchain()
    modes = active_build_modes()

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
        for mode in modes:
            out_cpp = mode_artifact(Path(f"{base}.cpp"), mode)
            log = mode_artifact(Path(f"{base}.compile.log"), mode)
            label = f"{relative(src)} [{mode}]"

            accepted = franko_compile(
                src, out_cpp, mode=mode, extra_flags=extra_flags,
                log=log, echo=False,
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

        for mode in modes:
            out_cpp = mode_artifact(Path(f"{base}.cpp"), mode)
            out_bin = mode_artifact(Path(f"{base}.bin{executable_suffix()}"), mode)
            actual = mode_artifact(Path(f"{base}.actual"), mode)
            log = mode_artifact(Path(f"{base}.compile.log"), mode)
            label = f"{relative(src)} [{mode}]"

            failed = franko_compile(
                src, out_cpp, mode=mode, extra_flags=extra_flags,
                log=log, echo=False,
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

            failed = native_compile(
                out_cpp, out_bin, mode=mode, warnings="full",
                log=log, echo=False,
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

        for mode in modes:
            out_cpp = mode_artifact(Path(f"{base}.cpp"), mode)
            out_bin = mode_artifact(Path(f"{base}.bin{executable_suffix()}"), mode)
            actual = mode_artifact(Path(f"{base}.actual"), mode)
            log = mode_artifact(Path(f"{base}.compile.log"), mode)
            label = f"{relative(src)} [{mode}]"

            failed = franko_compile(
                src, out_cpp, mode=mode, extra_flags=extra_flags,
                log=log, echo=False,
            )
            if failed != 0:
                print("---- compiler output: ----")
                dump_file(log)
                results.fail_case(f"{label} (Franko compile failed; runtime cases must compile)")
                continue

            failed = native_compile(
                out_cpp, out_bin, mode=mode, warnings="none",
                log=log, echo=False,
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
