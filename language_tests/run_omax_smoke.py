#!/usr/bin/env python3
"""-Omax smoke coverage.

    uv run python language_tests/run_omax_smoke.py

Compiles and runs representative success fixtures with -Omax, then pins
the bare static-index emission for the nested-array runtime case.
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT))

from franko_test import (
    Results,
    case_paths,
    dump_file,
    read_text,
    remove_artifacts,
    run_and_record,
)
from franko_tools import ToolError, relative, require_toolchain

from compile import franko_compile

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

BARE_STATIC_INDEX_CASE = ROOT / "language_tests/runtime/arrays/static_nested_index_oob.fr"


def main() -> int:
    require_toolchain()
    results = Results()

    for name in SUCCESS_CASES:
        src = ROOT / "language_tests" / "success" / name / "main.fr"
        run_and_record(results, src, mode="omax", tag=".omax")

    src = BARE_STATIC_INDEX_CASE
    case = case_paths(src, ".omax")
    label = f"{relative(src)} [omax] (bare static index)"
    print(f"=== {relative(src)} [omax, bare static index] ===")

    code = franko_compile(src, case.cpp, mode="omax", log=case.log, echo=False)
    if code != 0:
        print("---- compiler output: ----")
        dump_file(case.log)
        results.record(label, "Franko compile failed")
    else:
        generated = read_text(case.cpp)
        if "franko_static_array_at" in generated:
            print("---- generated C++ still calls franko_static_array_at: ----")
            dump_file(case.cpp)
            results.record(label, "checked helper emitted")
        elif "VAR_g[VAR_i])[VAR_j]" not in generated:
            print("---- generated C++ does not contain bare VAR_g[VAR_i][VAR_j]: ----")
            dump_file(case.cpp)
            results.record(label, "bare indexing missing")
        else:
            results.record(label)

    remove_artifacts(case.cpp, case.log)
    return results.finish("✅ All -Omax smoke tests passed.")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ToolError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
