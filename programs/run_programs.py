#!/usr/bin/env python3
"""Franko program smoke test.

Replaces programs/run_programs.sh and programs/run_programs.ps1; the same
command works on every platform:

    uv run python programs/run_programs.py

Compiles every program in programs/, runs it, and compares its stdout
against the checked-in <program>.expected file.

Every program in programs/ is a positive test. Negative checker tests live
in tests/ and are intentionally not part of this smoke test; run
`uv run python tests/run_specs_tests.py` for the full suite.

A program may provide programs/<name>.args: each line is one command-line
argument passed verbatim to the binary.

A program may provide programs/<name>.stdin: bytes fed to the binary on
stdin (carriage returns are stripped, so CRLF checkouts behave like LF
ones). This is what lets interactive-style programs that read stdin run
deterministically under the smoke test.

Generated artifacts (.cpp, .testbin, .actual) are removed after a passing
case and kept for inspection after a failure.
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))

from franko_tools import (
    IMPORT_DIR,
    INCLUDE_DIR,
    JAVA_HINT,
    Results,
    ToolError,
    compare_output,
    cxx_compile_flags,
    dump_file,
    executable_suffix,
    find_cxx,
    find_tool,
    franko_classpath,
    jemalloc_paths,
    link_flags,
    read_run_args,
    read_stdin_bytes,
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

    programs = sorted(ROOT.glob("programs/*.fr"))
    if not programs:
        raise ToolError("no .fr programs found under programs/")

    results = Results()

    for src in programs:
        base = src.with_suffix("")
        out_cpp = Path(f"{base}.cpp")
        out_bin = Path(f"{base}.testbin{executable_suffix()}")
        actual = Path(f"{base}.actual")
        expected = Path(f"{base}.expected")

        print()
        print(f"=== {relative(src)} ===")
        print()

        run_args = read_run_args(Path(f"{base}.args"))
        stdin_bytes = read_stdin_bytes(Path(f"{base}.stdin"))

        if run(
            [java, "-cp", franko_classpath(), "Main", src, "-o", out_cpp,
             "-p", IMPORT_DIR],
            check=False,
        ) != 0:
            results.fail_case(f"{relative(src)} (Franko compile failed)")
            continue

        if run(
            [cxx, *cxx_compile_flags("basic"), f"-I{INCLUDE_DIR}", f"-I{include}",
             out_cpp, jemalloc_lib, *link_flags(), "-o", out_bin],
            check=False,
        ) != 0:
            results.fail_case(f"{relative(src)} (C++ compile failed)")
            continue

        if out_bin.stat().st_size == 0:
            results.fail_case(f"{relative(src)} (linked binary is empty, 0 bytes)")
            continue

        exit_code = run_test_binary(out_bin, run_args, stdin_bytes, actual)
        if exit_code != 0:
            print(f"---- runtime output (exit code {exit_code}): ----")
            dump_file(actual)
            results.fail_case(f"{relative(src)} (binary exited non-zero)")
            continue

        print(f"Comparing output against {relative(expected)}:")
        if not compare_output(actual, expected):
            results.fail_case(f"{relative(src)} (output mismatch)")
            continue

        remove_artifacts(out_cpp, out_bin, actual)
        results.pass_case(
            f"{relative(src)} ran successfully, output matches {relative(expected)}"
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
