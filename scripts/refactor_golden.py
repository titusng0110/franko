#!/usr/bin/env python3
"""Capture and validate the compiler output golden corpus.

The data-oriented refactor must not change any observable compiler
output. This script records, for every program and test source, the
per-pass JSON artifacts, the generated C++ file, the compiler's stdout
and stderr, and its exit code. Two captures must compare byte-for-byte.

Usage:

    uv run python scripts/refactor_golden.py capture --out build/refactor-golden/baseline
    uv run python scripts/refactor_golden.py capture --out build/refactor-golden/stage-2
    diff -ru build/refactor-golden/baseline build/refactor-golden/stage-2

    uv run python scripts/refactor_golden.py self-test --dir build/refactor-golden/stage-2

The output layout mirrors the repository paths, with each ``.fr`` source
mapped to a directory named after it (without the suffix):

    <out>/programs/bfs/{out.cpp,out.raw.ast.json,...,stdout.txt,stderr.txt,exit.txt}
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))

from franko_tools import (
    IMPORT_DIR,
    ToolError,
    find_tool,
    franko_classpath,
    host_target,
    java_mode_flags,
    read_flags,
    relative,
    require_toolchain,
)


CATEGORY_GLOBS = (
    ("programs", "programs/*.fr", None),
    ("language-success", "language_tests/success/*/main.fr", "main.flags"),
    ("language-failure", "language_tests/failure/*/*.fr", None),
    ("language-runtime", "language_tests/runtime/*/*.fr", None),
    ("library", "library_tests/**/main.fr", "main.flags"),
)


def case_flags(source: Path, flags_name: str | None) -> list[str]:
    if flags_name == "main.flags":
        flags_path = source.parent / "main.flags"
    else:
        flags_path = Path(f"{source.with_suffix('')}.flags")

    return read_flags(flags_path) if flags_path.is_file() else []


def discover_cases(filters: list[str]) -> list[tuple[str, Path, list[str]]]:
    cases: list[tuple[str, Path, list[str]]] = []

    for category, pattern, flags_name in CATEGORY_GLOBS:
        for source in sorted(ROOT.glob(pattern)):
            if not source.is_file():
                continue
            key = relative(source)
            if filters and not any(part in key for part in filters):
                continue
            cases.append((category, source, case_flags(source, flags_name)))

    return cases


def case_key(source: Path) -> Path:
    return source.resolve().relative_to(ROOT).with_suffix("")


def capture(args: argparse.Namespace) -> int:
    require_toolchain()

    out = args.out.resolve()
    if out.exists():
        if not args.force:
            raise ToolError(
                f"output directory already exists: {relative(out)} "
                "(pass --force to replace it)"
            )
        shutil.rmtree(out)
    out.mkdir(parents=True)

    java = find_tool("java")
    classpath = franko_classpath()
    target = host_target()
    mode_flags = java_mode_flags(args.mode)

    cases = discover_cases(args.filter or [])
    failures = 0

    for index, (category, source, flags) in enumerate(cases, start=1):
        case_dir = out / case_key(source)
        case_dir.mkdir(parents=True, exist_ok=True)

        source_arg = relative(source)
        cpp_path = case_dir / "out.cpp"
        command = [
            str(java),
            "-cp",
            classpath,
            "Main",
            source_arg,
            "-o",
            str(cpp_path),
            "-p",
            str(IMPORT_DIR),
            "--target",
            target,
            "--emit-json",
            *mode_flags,
            *flags,
        ]

        completed = subprocess.run(
            command,
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

        (case_dir / "stdout.txt").write_text(
            completed.stdout.replace(str(case_dir), "$CASE"), encoding="utf-8"
        )
        (case_dir / "stderr.txt").write_text(
            completed.stderr.replace(str(case_dir), "$CASE"), encoding="utf-8"
        )
        (case_dir / "exit.txt").write_text(f"{completed.returncode}\n", encoding="utf-8")
        if completed.returncode not in (0, 1):
            failures += 1
            print(
                f"[{index}/{len(cases)}] {category}: {source_arg} "
                f"(exit {completed.returncode})"
            )

    print(f"captured {len(cases)} cases under {relative(out)}")
    if failures:
        print(f"{failures} case(s) exited with an unexpected status")
        return 1
    return 0


def self_test(args: argparse.Namespace) -> int:
    require_toolchain()

    directory = args.dir.resolve()
    if not directory.is_dir():
        raise ToolError(f"not a directory: {relative(directory)}")

    java = find_tool("java")
    classpath = franko_classpath()
    artifacts = sorted(directory.rglob("*.json"))

    if not artifacts:
        raise ToolError(f"no .json artifacts under {relative(directory)}")

    checked = 0
    failures = 0

    for artifact in artifacts:
        completed = subprocess.run(
            [str(java), "-cp", classpath, "CompilerJsonSelfTest", str(artifact)],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        checked += 1
        if completed.returncode != 0:
            failures += 1
            print(f"FAIL: {relative(artifact)}")
            print(completed.stderr.rstrip())

    print(f"self-test checked {checked} artifact(s), {failures} failure(s)")
    return 1 if failures else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    capture_parser = subparsers.add_parser("capture")
    capture_parser.add_argument("--out", type=Path, required=True)
    capture_parser.add_argument("--force", action="store_true")
    capture_parser.add_argument(
        "--filter", action="append", default=[],
        help="only capture cases whose repository path contains this substring",
    )
    capture_parser.add_argument(
        "--mode", choices=("asan", "opt", "omax"), default="asan",
        help="Franko compiler optimization mode (default: asan, i.e. no flag)",
    )
    capture_parser.set_defaults(func=capture)

    self_test_parser = subparsers.add_parser("self-test")
    self_test_parser.add_argument("--dir", type=Path, required=True)
    self_test_parser.set_defaults(func=self_test)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ToolError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
