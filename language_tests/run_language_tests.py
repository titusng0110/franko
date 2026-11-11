#!/usr/bin/env python3
"""Franko language test suite.

    uv run python language_tests/run_language_tests.py

Fixture layout:

  failure/<family>/<case>.fr
    Must be rejected. Optional <case>.errors pins diagnostics.

  success/<concept>/main.fr
    Must compile, run, and match main.expected. Optional main.warnings,
    main.forbidden, main.flags, and main.args.

  runtime/<family>/<case>.fr
    Must compile, but the binary must exit non-zero.

Generated artifacts are removed after a passing case and kept after a
failure.
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT))

from franko_test import Results, run_and_record
from franko_tools import ToolError, require_toolchain

from compile import default_build_mode


def main() -> int:
    require_toolchain()
    mode = default_build_mode()
    results = Results()

    for src in sorted(ROOT.glob("language_tests/failure/*/*.fr")):
        run_and_record(results, src, mode=mode, kind="failure")

    for src in sorted(ROOT.glob("language_tests/success/*/main.fr")):
        run_and_record(results, src, mode=mode)

    for src in sorted(ROOT.glob("language_tests/runtime/*/*.fr")):
        run_and_record(results, src, mode=mode, kind="runtime")

    return results.finish("✅ All language tests passed.")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ToolError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
