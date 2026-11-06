#!/usr/bin/env python3
"""-Omax smoke coverage.

The normal language suite exercises only the "asan" and "opt" build
modes. This runner covers the third shipping runtime, -Omax, where
static-array bounds checks (and the other range/bit/offset checks) are
intentionally stripped:

    uv run python language_tests/run_omax_smoke.py

It compiles, runs, and output-compares a representative set of success
fixtures (static arrays, nested arrays, dynamic arrays of static rows,
decayed static views, byte intrinsics), then compiles a nested static
index fixture and checks the generated C++ uses bare indexing instead
of franko_static_array_at. Runtime-abort fixtures are not run here:
out-of-range access is undefined behavior in -Omax by design.
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
    compare_output,
    dump_file,
    executable_suffix,
    read_text,
    relative,
    remove_artifacts,
    require_toolchain,
    run_test_binary,
)

# Success fixtures covering the emission paths that only -Omax changes
# or that the new runtime is most sensitive to.
SUCCESS_CASES = (
    "arr_static_sizes",
    "arr_nested",
    "init_list_nested",
    "array_view_decay",
    "byte_bit_ops",
    "byte_endian_access",
    "byte_memchr_memmem",
    "byte_static_views",
    "enums_defaults",
)

# Compiled (not run) to pin the bare static-index emission.
BARE_STATIC_INDEX_CASE = (
    ROOT / "language_tests/runtime/arrays/static_nested_index_oob.fr"
)


def out_cpp_for(src: Path) -> Path:
    return Path(f"{src.with_suffix('')}.omax.cpp")


def out_bin_for(src: Path) -> Path:
    return Path(f"{src.with_suffix('')}.omax.bin{executable_suffix()}")


def actual_for(src: Path) -> Path:
    return Path(f"{src.with_suffix('')}.omax.actual")


def log_for(src: Path) -> Path:
    return Path(f"{src.with_suffix('')}.omax.compile.log")


def main() -> int:
    require_toolchain()

    results = Results()

    for name in SUCCESS_CASES:
        src = ROOT / "language_tests/success" / name / "main.fr"
        expected = Path(f"{src.with_suffix('')}.expected")

        print(f"=== {relative(src)} [omax] ===")

        out_cpp = out_cpp_for(src)
        out_bin = out_bin_for(src)
        actual = actual_for(src)
        log = log_for(src)
        label = f"{relative(src)} [omax]"

        failed = franko_compile(
            src, out_cpp, mode="omax", log=log, echo=False,
        )
        if failed != 0:
            print("---- compiler output: ----")
            dump_file(log)
            results.fail_case(f"{label} (Franko compile failed)")
            continue

        failed = native_compile(
            out_cpp, out_bin, mode="omax", warnings=True,
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

        exit_code = run_test_binary(out_bin, [], None, actual)
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

    src = BARE_STATIC_INDEX_CASE
    print(f"=== {relative(src)} [omax, bare static index] ===")

    out_cpp = out_cpp_for(src)
    log = log_for(src)
    label = f"{relative(src)} [omax] (bare static index)"

    failed = franko_compile(
        src, out_cpp, mode="omax", log=log, echo=False,
    )
    if failed != 0:
        print("---- compiler output: ----")
        dump_file(log)
        results.fail_case(f"{label} (Franko compile failed)")
    else:
        generated = read_text(out_cpp)

        if "franko_static_array_at" in generated:
            print("---- generated C++ still calls franko_static_array_at: ----")
            dump_file(out_cpp)
            results.fail_case(f"{label} (checked helper emitted)")
        elif "VAR_g[VAR_i])[VAR_j]" not in generated:
            print("---- generated C++ does not contain bare VAR_g[VAR_i][VAR_j]: ----")
            dump_file(out_cpp)
            results.fail_case(f"{label} (bare indexing missing)")
        else:
            results.pass_case(label)

    remove_artifacts(out_cpp, log)

    return results.finish("✅ All -Omax smoke tests passed.")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ToolError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
