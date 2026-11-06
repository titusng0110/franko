#!/usr/bin/env python3
"""Reject Java constructs outside the Franko compiler's permitted subset.

Only the hand-written compiler sources in the repository root are scanned.
Generated ANTLR code under build/generated is exempt.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

CHECKS = (
    ("interface declaration", re.compile(r"^\s*(?:public\s+|private\s+|protected\s+)?interface\s+\w+", re.M)),
    ("record declaration", re.compile(r"^\s*(?:public\s+|private\s+|protected\s+)?record\s+\w+", re.M)),
    ("sealed declaration", re.compile(r"\bsealed\b|\bpermits\b")),
    ("implements clause", re.compile(r"\bimplements\b")),
    ("var declaration", re.compile(r"\bvar\s+[A-Za-z_$][\w$]*\s*=")),
    ("method reference", re.compile(r"[A-Za-z_$][\w$]*\s*::")),
    ("stream usage", re.compile(r"\.stream\s*\(|\bCollectors\b")),
    ("switch expression", re.compile(r"\breturn\s+switch\b|=\s*switch\b|\byield\b")),
    ("pattern instanceof", re.compile(r"\binstanceof\s+[A-Za-z_$][\w$.]*(?:\s*<[^;{}()]*>)?\s+[A-Za-z_$][\w$]*")),
    ("line comment", re.compile(r"//")),
    ("block comment", re.compile(r"/\*")),
)

ALLOWED_EXTENDS = re.compile(
    r"public\s+class\s+FrankoASTVisitor\s+extends\s+FrankoBaseVisitor\s*<",
)


def mask_literals(text: str) -> str:
    out = list(text)
    i = 0

    while i < len(out):
        c = text[i]

        if c in ('"', "'"):
            quote = c
            i += 1

            while i < len(out):
                if text[i] == '\\':
                    out[i] = ' '
                    i += 1
                    if i < len(out):
                        out[i] = ' '
                        i += 1
                    continue

                if text[i] == quote:
                    i += 1
                    break

                if text[i] != '\n':
                    out[i] = ' '
                i += 1

            continue

        i += 1

    return ''.join(out)


def scan() -> int:
    failures = 0

    for path in sorted(ROOT.glob("*.java")):
        text = path.read_text(encoding="utf-8")
        masked = mask_literals(text)
        lines = text.splitlines()

        for label, pattern in CHECKS:
            for match in pattern.finditer(masked):
                line = text.count("\n", 0, match.start()) + 1
                source_line = lines[line - 1].strip()
                print(f"{path.name}:{line}: {label}: {source_line}")
                failures += 1

        for match in re.finditer(r"\bextends\b", masked):
            line_start = text.rfind("\n", 0, match.start()) + 1
            line_end = text.find("\n", match.start())
            if line_end < 0:
                line_end = len(text)
            line = text[line_start:line_end]
            if ALLOWED_EXTENDS.search(line):
                continue
            line_no = text.count("\n", 0, match.start()) + 1
            print(f"{path.name}:{line_no}: extends clause: {line.strip()}")
            failures += 1

    if failures:
        print(f"\n{failures} Java subset violation(s)")
        return 1

    print("Java subset check passed")
    return 0


if __name__ == "__main__":
    sys.exit(scan())
