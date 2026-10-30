#!/usr/bin/env python3
"""Franko program smoke test.

Replaces programs/run_programs.sh and programs/run_programs.ps1; the same
command works on every platform:

    uv run python programs/run_programs.py

Compiles every program in programs/, runs it, and compares its stdout
against the checked-in <program>.expected file.

Every program in programs/ is a positive test. Negative checker tests live
in language_tests/ and are intentionally not part of this smoke test; run
`uv run python language_tests/run_language_tests.py` for the full suite.

A program may provide programs/<name>.args: each line is one command-line
argument passed verbatim to the binary.

A program may provide programs/<name>.stdin: bytes fed to the binary on
stdin (carriage returns are stripped, so CRLF checkouts behave like LF
ones). This is what lets interactive-style programs that read stdin run
deterministically under the smoke test.

Generated artifacts (.cpp, .testbin, .actual) are removed after a passing
case and kept for inspection after a failure.

Every program builds and runs in each active build mode (see
franko_tools.active_build_modes): ASan/system-allocator mode (Franko
without -O) and optimized mode (Franko -O with jemalloc) on
Linux/macOS; optimized mode only on Windows, where AddressSanitizer is
unsupported. Both stages build through compile.franko_compile /
compile.native_compile, shared with compile.py and the other suites.
The optimized build keeps the historical artifact names; the ASan
build uses .asan-suffixed ones.
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
    compare_output,
    dump_file,
    executable_suffix,
    mode_artifact,
    read_run_args,
    read_stdin_bytes,
    relative,
    remove_artifacts,
    require_toolchain,
    run_test_binary,
)


def main() -> int:
    require_toolchain()
    modes = active_build_modes()

    programs = sorted(ROOT.glob("programs/*.fr"))
    if not programs:
        raise ToolError("no .fr programs found under programs/")

    results = Results()

    for src in programs:
        base = src.with_suffix("")
        expected = Path(f"{base}.expected")

        print()
        print(f"=== {relative(src)} ===")
        print()

        run_args = read_run_args(Path(f"{base}.args"))
        stdin_bytes = read_stdin_bytes(Path(f"{base}.stdin"))

        for mode in modes:
            out_cpp = mode_artifact(Path(f"{base}.cpp"), mode)
            out_bin = mode_artifact(Path(f"{base}.testbin{executable_suffix()}"), mode)
            actual = mode_artifact(Path(f"{base}.actual"), mode)
            label = f"{relative(src)} [{mode}]"

            if franko_compile(src, out_cpp, mode=mode, echo=True) != 0:
                results.fail_case(f"{label} (Franko compile failed)")
                continue

            if native_compile(out_cpp, out_bin, mode=mode, warnings=True,
                               echo=True) != 0:
                results.fail_case(f"{label} (C++ compile failed)")
                continue

            if out_bin.stat().st_size == 0:
                results.fail_case(f"{label} (linked binary is empty, 0 bytes)")
                continue

            exit_code = run_test_binary(out_bin, run_args, stdin_bytes, actual)
            if exit_code != 0:
                print(f"---- runtime output (exit code {exit_code}): ----")
                dump_file(actual)
                results.fail_case(f"{label} (binary exited non-zero)")
                continue

            print(f"Comparing output against {relative(expected)}:")
            if not compare_output(actual, expected):
                results.fail_case(f"{label} (output mismatch)")
                continue

            remove_artifacts(out_cpp, out_bin, actual)
            results.pass_case(
                f"{label} ran successfully, output matches {relative(expected)}"
            )

    return results.finish("✅ All programs passed.")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ToolError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
