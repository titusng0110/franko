#!/usr/bin/env python3
"""Franko library test suite.

    uv run python library_tests/run_library_tests.py

Each suite under library_tests/<module>/<suite>/ has the same
success/failure/runtime fixture layout as the language suite. The
`win32` suite runs only on Windows and the `posix` suite only elsewhere.
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

    for pattern, kind in (
        ("library_tests/*/*/failure/*/main.fr", "failure"),
        ("library_tests/*/*/success/*/main.fr", "success"),
        ("library_tests/*/*/runtime/*/main.fr", "runtime"),
    ):
        for src in sorted(ROOT.glob(pattern)):
            if not skip_platform_suite(src, results):
                run_and_record(results, src, mode=mode, kind=kind)

    return results.finish("✅ All library tests passed.")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ToolError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
