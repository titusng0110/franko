#!/usr/bin/env python3
"""Franko program smoke test.

    uv run python programs/run_programs.py

Compiles every programs/*.fr, runs it, and compares stdout with the
matching .expected file. A program may provide .args and .stdin files.
Generated C++ is kept after a passing case; binaries and captured output
are removed.
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

    programs = sorted(ROOT.glob("programs/*.fr"))
    if not programs:
        raise ToolError("no .fr programs found under programs/")

    for src in programs:
        print()
        run_and_record(
            results,
            src,
            mode=mode,
            bin_name=".testbin",
            capture=False,
            keep_cpp=True,
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
