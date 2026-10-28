#!/usr/bin/env python3
"""Rebuild the Franko compiler toolchain (ANTLR parser + Java classes).

Replaces update_linux-x86_64.sh and update_windows-x64.ps1; the same
command works on Linux, macOS and Windows:

    uv run python update_toolchain.py

Run it whenever the grammar (Franko.g4) or the compiler sources change.
The steps are not interactive, so it is also what CI uses to build the
toolchain.
"""

import argparse
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "scripts"))

from franko_tools import ToolError, build_toolchain, ensure_jemalloc_bundle


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Rebuild the Franko compiler toolchain "
                    "(ANTLR parser + Java classes).",
    )
    parser.add_argument(
        "--java-release",
        default=os.environ.get("FRANKO_JAVA_RELEASE", "25"),
        help="javac --release value for the compiler classes "
             "(default: $FRANKO_JAVA_RELEASE, else 25)",
    )
    args = parser.parse_args()
    ensure_jemalloc_bundle()
    build_toolchain(java_release=args.java_release)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ToolError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
