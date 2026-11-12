#!/usr/bin/env python3
"""Franko library test suite.

    uv run python library_tests/run_library_tests.py

Library tests are success-focused: each library has one free-function fixture,
and each struct has one fixture covering all of its methods together. Additional
runtime fixtures cover functions whose contract is to abort, such as
`std::panic`. There are no compiler-failure fixtures; a library failing to
compile is a compiler bug.
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT))

from franko_test import Results, run_and_record
from franko_tools import IS_WINDOWS, ToolError, relative, require_toolchain

from compile import default_build_mode


def suite_name(src: Path) -> str:
    return src.relative_to(ROOT).parts[2]


def skip_platform_suite(src: Path, results: Results) -> bool:
    suite = suite_name(src)
    if suite == "win32" and not IS_WINDOWS:
        results.skip(relative(src), "windows-only suite")
        return True
    if suite == "posix" and IS_WINDOWS:
        results.skip(relative(src), "posix suite does not run on Windows")
        return True
    return False


def main() -> int:
    require_toolchain()
    mode = default_build_mode()
    results = Results()

    for src in sorted(ROOT.glob("library_tests/*/*/main.fr")):
        if not skip_platform_suite(src, results):
            run_and_record(results, src, mode=mode)

    for src in sorted(ROOT.glob("library_tests/*/*/runtime/main.fr")):
        if not skip_platform_suite(src, results):
            run_and_record(results, src, mode=mode, kind="runtime")

    return results.finish("✅ All library tests passed.")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ToolError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
