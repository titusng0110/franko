#!/usr/bin/env python3
"""Franko program smoke test.

Replaces programs/run_programs.sh and programs/run_programs.ps1; the same
command works on every platform:

    uv run python programs/run_programs.py

Compiles every top-level program in programs/, runs it, and compares its
stdout against the checked-in <program>.expected file. Programs are
self-contained; no shared helper directory is scanned.

Every top-level program in programs/ is a positive test. Negative checker
tests live in language_tests/ and are intentionally not part of this smoke
test; run
`uv run python language_tests/run_language_tests.py` for the full suite.

A program may provide programs/<name>.args: each line is one command-line
argument passed verbatim to the binary.

A program may provide programs/<name>.stdin: bytes fed to the binary on
stdin (carriage returns are stripped, so CRLF checkouts behave like LF
ones). This is what lets interactive-style programs that read stdin run
deterministically under the smoke test.

Generated C++ sources are kept for inspection after a passing case (they
are git-ignored); only the test binaries and captured .actual output are
removed. All artifacts are kept for inspection after a failure.

Every program builds and runs in the default build mode (see
franko_tools.default_build_mode): ASan/debug where supported, or the
optimized mode with the Windows g++/clang++ fallback, which has no
AddressSanitizer. Test builds use the system allocator; jemalloc is an
opt-in compile.py flag on Linux. Both stages build through
compile.franko_compile / compile.native_compile, shared with compile.py
and the other suites.
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT))

from franko_tools import (
    Results,
    ToolError,
    compare_output,
    default_build_mode,
    dump_file,
    executable_suffix,
    read_run_args,
    read_stdin_bytes,
    relative,
    remove_artifacts,
    require_toolchain,
    run_test_binary,
)

from compile import franko_compile, native_compile


def main() -> int:
    require_toolchain()
    mode = default_build_mode()

    programs = sorted(ROOT.glob("programs/*.fr"))
    if not programs:
        raise ToolError("no .fr programs found under programs/")

    results = Results()

    for src in programs:
        base = src.with_suffix("")
        expected = Path(f"{base}.expected")
        out_cpp = Path(f"{base}.cpp")
        out_bin = Path(f"{base}.testbin{executable_suffix()}")
        actual = Path(f"{base}.actual")
        label = f"{relative(src)} [{mode}]"

        print()
        print(f"=== {relative(src)} ===")
        print()

        run_args = read_run_args(Path(f"{base}.args"))
        stdin_bytes = read_stdin_bytes(Path(f"{base}.stdin"))

        if franko_compile(src, out_cpp, mode=mode, echo=True) != 0:
            results.fail_case(f"{label} (Franko compile failed)")
            continue

        if native_compile(out_cpp, out_bin, mode=mode, echo=True) != 0:
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

        remove_artifacts(out_bin, actual)
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
