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
rendering varies across runs or platforms (such as live addresses) in a
.expected file; cover those through the runtime category, which is judged
by exit status only.

Generated artifacts (.cpp, .bin, .actual, compiler logs) are removed after
a passing case and kept for inspection after a failure.
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))

from franko_tools import (
    INCLUDE_DIR,
    JAVA_HINT,
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
    jemalloc_paths,
    link_flags,
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
    # Failure cases: the compiler must reject the program.
    # ------------------------------------------------------------
    for src in sorted(ROOT.glob("language_tests/failure/*/*.fr")):
        base = src.with_suffix("")
        out_cpp = Path(f"{base}.cpp")
        log = Path(f"{base}.compile.log")
        errors = Path(f"{base}.errors")

        print(f"=== {relative(src)} ===")

        extra_flags = read_flags(Path(f"{base}.flags"))
        accepted = run(
            [java, "-cp", franko_classpath(), "Main", src, "-o", out_cpp,
             *extra_flags],
            log_file=log, echo=False, check=False,
        )

        if accepted == 0:
            print("---- compiler output (accepted a failure program): ----")
            dump_file(log)
            remove_artifacts(out_cpp, log)
            results.fail_case(relative(src))
            continue

        if errors.is_file() and not check_substrings(errors, read_text(log)):
            print("---- compiler output (missing expected diagnostics): ----")
            dump_file(log)
            results.fail_case(f"{relative(src)} (missing expected diagnostics)")
            continue

        remove_artifacts(log, out_cpp)
        results.pass_case(relative(src))

    # ------------------------------------------------------------
    # Success cases: compile, run, compare stdout with main.expected.
    # ------------------------------------------------------------
    for src in sorted(ROOT.glob("language_tests/success/*/main.fr")):
        base = src.with_suffix("")
        out_cpp = Path(f"{base}.cpp")
        out_bin = Path(f"{base}.bin{executable_suffix()}")
        actual = Path(f"{base}.actual")
        log = Path(f"{base}.compile.log")
        expected = Path(f"{base}.expected")
        warnings = Path(f"{base}.warnings")

        print(f"=== {relative(src)} ===")

        extra_flags = read_flags(Path(f"{base}.flags"))
        run_args = read_run_args(Path(f"{base}.args"))

        failed = run(
            [java, "-cp", franko_classpath(), "Main", src, "-o", out_cpp,
             *extra_flags],
            log_file=log, echo=False, check=False,
        )
        if failed != 0:
            print("---- compiler output: ----")
            dump_file(log)
            results.fail_case(f"{relative(src)} (Franko compile failed)")
            continue

        if warnings.is_file() and not check_substrings(warnings, read_text(log)):
            print("---- compiler output (missing expected warnings): ----")
            dump_file(log)
            results.fail_case(f"{relative(src)} (missing expected warnings)")
            continue

        failed = run(
            [cxx, *cxx_compile_flags("full"), f"-I{INCLUDE_DIR}", f"-I{include}",
             out_cpp, jemalloc_lib, *link_flags(), "-o", out_bin],
            log_file=log, echo=False, check=False,
        )
        if failed != 0:
            print("---- g++ output: ----")
            dump_file(log)
            results.fail_case(f"{relative(src)} (C++ compile failed)")
            continue

        if out_bin.stat().st_size == 0:
            results.fail_case(f"{relative(src)} (linked binary is empty, 0 bytes)")
            continue

        exit_code = run_test_binary(out_bin, run_args, None, actual)
        if exit_code != 0:
            print(f"---- runtime output (exit code {exit_code}): ----")
            dump_file(actual)
            results.fail_case(f"{relative(src)} (binary exited non-zero)")
            continue

        if not compare_output(actual, expected):
            results.fail_case(f"{relative(src)} (output mismatch)")
            continue

        remove_artifacts(out_cpp, out_bin, log, actual)
        results.pass_case(relative(src))

    # ------------------------------------------------------------
    # Runtime cases: the program compiles but must abort at run time.
    # ------------------------------------------------------------
    for src in sorted(ROOT.glob("language_tests/runtime/*/*.fr")):
        base = src.with_suffix("")
        out_cpp = Path(f"{base}.cpp")
        out_bin = Path(f"{base}.bin{executable_suffix()}")
        actual = Path(f"{base}.actual")
        log = Path(f"{base}.compile.log")

        print(f"=== {relative(src)} ===")

        extra_flags = read_flags(Path(f"{base}.flags"))

        failed = run(
            [java, "-cp", franko_classpath(), "Main", src, "-o", out_cpp,
             *extra_flags],
            log_file=log, echo=False, check=False,
        )
        if failed != 0:
            print("---- compiler output: ----")
            dump_file(log)
            results.fail_case(f"{relative(src)} (Franko compile failed; runtime cases must compile)")
            continue

        failed = run(
            [cxx, *cxx_compile_flags("none"), f"-I{INCLUDE_DIR}", f"-I{include}",
             out_cpp, jemalloc_lib, *link_flags(), "-o", out_bin],
            log_file=log, echo=False, check=False,
        )
        if failed != 0:
            print("---- g++ output: ----")
            dump_file(log)
            results.fail_case(f"{relative(src)} (C++ compile failed)")
            continue

        if out_bin.stat().st_size == 0:
            results.fail_case(f"{relative(src)} (linked binary is empty, 0 bytes)")
            continue

        # The abort itself is the assertion: any non-zero exit counts,
        # including the negative code a signal produces.
        exit_code = run_test_binary(out_bin, [], None, actual)
        if exit_code == 0:
            print("---- program output (expected a runtime failure, got exit 0): ----")
            dump_file(actual)
            remove_artifacts(out_cpp, out_bin, log, actual)
            results.fail_case(f"{relative(src)} (expected a runtime failure, exit 0)")
            continue

        remove_artifacts(out_cpp, out_bin, log, actual)
        results.pass_case(relative(src))

    return results.finish("✅ All language tests passed.")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ToolError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
