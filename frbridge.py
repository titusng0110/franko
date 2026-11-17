#!/usr/bin/env python3
"""Generate an FFI bridge template for a Franko module.

    uv run python frbridge.py lib/mathx.fr
    uv run python frbridge.py lib/mathx.fr -o lib/mathx.ffi.cpp
    uv run python frbridge.py lib/mathx.fr --force

The module's sidecar <name>.module.json, when present, selects the bridge
path and the functions that receive blocks. Without a descriptor, every
non-main function declared in the module gets a block.

The generated file is a starting point: each block contains the exact
emitted C++ name and signature, plus a TODO body that must be replaced.
The generator analyzes every target and merges the selected declarations,
so target-conditional functions all receive blocks.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "scripts"))

from franko_tools import (  # noqa: E402
    JAVA_HINT,
    ToolError,
    ensure_toolchain,
    find_tool,
    franko_classpath,
    relative,
)

IMPORT_DIR = ROOT / "import"


def descriptor_path(module: Path) -> Path:
    return module.with_name(module.stem + ".module.json")


def default_output(module: Path) -> Path:
    manifest = descriptor_path(module)

    if manifest.is_file():
        try:
            data = json.loads(manifest.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise ToolError(f"cannot read descriptor {relative(manifest)}: {exc}")

        bridge = data.get("bridge")
        if isinstance(bridge, str) and bridge:
            return (manifest.parent / bridge).resolve()

    return module.with_name(module.stem + ".ffi.cpp")


def generate(module: Path, import_dir: Path) -> str:
    ensure_toolchain()
    command = [
        str(find_tool("java", JAVA_HINT)),
        "-cp",
        franko_classpath(),
        "FfiTemplateGenerator",
        str(module),
        str(import_dir),
    ]

    completed = subprocess.run(
        command,
        cwd=ROOT,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        encoding="utf-8",
        errors="replace",
    )

    if completed.stderr:
        print(completed.stderr, file=sys.stderr, end="")

    if completed.returncode != 0:
        raise ToolError(
            f"bridge generation failed with exit code {completed.returncode}"
        )

    return completed.stdout


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("module", help="Franko module source (.fr)")
    parser.add_argument(
        "-o",
        "--output",
        help="output bridge file (default: descriptor bridge or <module>.ffi.cpp)",
    )
    parser.add_argument(
        "-p",
        "--import-dir",
        default=str(IMPORT_DIR),
        help="import search directory (default: ./import)",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="overwrite the output file if it already exists",
    )
    args = parser.parse_args()

    module = Path(args.module).resolve()

    if not module.is_file():
        raise ToolError(f"module not found: {args.module}")

    if module.suffix != ".fr":
        raise ToolError(f"expected a .fr module, got {relative(module)}")

    import_dir = Path(args.import_dir).resolve()
    output = Path(args.output).resolve() if args.output else default_output(module)

    if output.exists() and not args.force:
        raise ToolError(
            f"output already exists: {relative(output)}\n"
            "Pass --force to overwrite it."
        )

    text = generate(module, import_dir)

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(text, encoding="utf-8")

    print(f"wrote {relative(output)}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ToolError as exc:
        print(f"frbridge: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
