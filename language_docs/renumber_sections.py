#!/usr/bin/env python3
"""
renumber_sections.py — propagate section numbers across the split Franko docs.

The split reference uses section-prefixed filenames (NN-slug.md), numbered
headings (`# NN. Title`, `## NN.K Subsection`), and three navigation files
that repeat the numbering: SUMMARY.md, 00-title-and-toc.md, and README.md.

When a chapter is inserted (or removed) in the middle, every later section
must shift. Doing that by hand means touching every file name, every heading,
every cross-link, and every navigation entry. This script does it in one go:

    .venv/bin/python renumber_sections.py insert-at 24     # make room at 24
    .venv/bin/python renumber_sections.py insert-at 24 -n  # preview only
    .venv/bin/python renumber_sections.py remove-at 24     # close the gap at 24
    .venv/bin/python renumber_sections.py check            # validate consistency

What insert-at N does (delta = +1):

  * renames every `NN-slug.md` with NN >= N to `(NN+1)-slug.md`,
  * renumbers headings whose leading section number is >= N in every document
    (`# 24. Del...` -> `# 25. Del...`, `## 24.1 ...` -> `## 25.1 ...`),
  * rewrites markdown link targets and backticked filename mentions that
    point at renamed files, in every document,
  * renumbers the entries in SUMMARY.md (`- [24. ...]` -> `- [25. ...]`),
    00-title-and-toc.md (`24. [...]` -> `25. [...]`), and README.md
    (`24. Title` -> `25. Title`).

remove-at N is the inverse (delta = -1 applied to sections > N). It does not
delete `N-slug.md` itself; delete or merge that file's content by hand.

Rules:

  * fenced code blocks (``` ... ```) are never modified,
  * Franko_Language_Reference.md is never modified (regenerate it afterwards
    with build_reference.py),
  * .venv/ and hidden files are ignored.

Uses the `regex` module (declared in the repository's pyproject.toml, so
`uv run python language_docs/renumber_sections.py <args>` works from the
repository root with no extra setup).
"""

import argparse
import sys
from pathlib import Path

try:
    import regex as re
except ImportError:  # pragma: no cover
    sys.exit(
        "error: the 'regex' package is required.\n"
        "Easiest: run it through uv from the repository root, where\n"
        "`regex` is declared in pyproject.toml:\n"
        "  uv run python language_docs/renumber_sections.py ...\n"
        "Or install it into the current interpreter:\n"
        "  uv pip install regex"
    )

ROOT = Path(__file__).resolve().parent

GENERATED = "Franko_Language_Reference.md"

# Files that repeat the section numbering outside of headings.
NAV_SUMMARY = "SUMMARY.md"
NAV_TOC = "00-title-and-toc.md"
NAV_README = "README.md"

# `NN-slug.md`
CONTENT_NAME = re.compile(r"^(\d{2})-(.+)\.md$")

# `# 24. Title` (dot after the section number)
HEADING_DOTTED = re.compile(r"^(#{1,6})(\s+)(\d+)((?:\.\d+)*)(\.)(\s|$)")
# `## 24.1 Title` / `### 24.1.2 Title` (no dot directly after the number)
HEADING_PLAIN = re.compile(r"^(#{1,6})(\s+)(\d+)((?:\.\d+)+)(\s|$)")

# `- [24. Title](24-slug.md)` in SUMMARY.md
SUMMARY_ITEM = re.compile(r"^(\s*-\s*\[)(\d+)(\.\s)")
# `24. [Title](24-slug.md)` in 00-title-and-toc.md
TOC_ITEM = re.compile(r"^(\s*)(\d+)(\.\s+\[)")
# `24. Title` in README.md
README_ITEM = re.compile(r"^(\s*)(\d+)(\.\s+)(\S)")

FENCE = re.compile(r"^\s*(```|~~~)")


def content_files():
    """All split content files (NN-slug.md), excluding the generated blob."""
    files = []
    for path in sorted(ROOT.glob("*.md")):
        if path.name == GENERATED:
            continue
        if CONTENT_NAME.match(path.name):
            files.append(path)
    return files


def walk_targets():
    """Every markdown file whose text may need renumbering."""
    targets = []
    for path in sorted(ROOT.glob("*.md")):
        if path.name == GENERATED:
            continue
        targets.append(path)
    return targets


def map_filenames(text, renames):
    """Replace exact occurrences of renamed filenames anywhere in a line."""
    for old, new in renames.items():
        if old in text:
            text = text.replace(old, new)
    return text


def bump_heading(line, threshold, delta):
    """Renumber a markdown heading's leading section number when >= threshold.

    Only the leading number is spliced; everything after it (including the
    whitespace separator) is preserved byte-for-byte.
    """
    m = HEADING_DOTTED.match(line)
    if not m:
        m = HEADING_PLAIN.match(line)
    if not m:
        return line

    first = int(m.group(3))
    if first < threshold:
        return line

    return line[:m.start(3)] + str(first + delta) + line[m.end(3):]


def bump_nav_item(line, threshold, delta, filename):
    """Renumber numbered entries in the three navigation files."""
    if filename == NAV_SUMMARY:
        m = SUMMARY_ITEM.match(line)
        if m:
            first = int(m.group(2))
            if first >= threshold:
                return f"{m.group(1)}{first + delta}{line[m.end(2):]}"
        return line

    if filename == NAV_TOC:
        m = TOC_ITEM.match(line)
        if m:
            first = int(m.group(2))
            if first >= threshold:
                return f"{m.group(1)}{first + delta}{line[m.end(2):]}"
        return line

    if filename == NAV_README:
        m = README_ITEM.match(line)
        if m:
            first = int(m.group(2))
            if first >= threshold:
                return f"{m.group(1)}{first + delta}{line[m.end(2):]}"
        return line

    return line


def transform_line(line, renames, threshold, delta, filename):
    line = map_filenames(line, renames)
    line = bump_heading(line, threshold, delta)
    line = bump_nav_item(line, threshold, delta, filename)
    return line


def transform_text(text, renames, threshold, delta, filename):
    """Apply per-line transforms, skipping fenced code blocks."""
    out = []
    in_fence = False
    changed = 0
    for line in text.splitlines(keepends=True):
        if FENCE.match(line):
            in_fence = not in_fence
            out.append(line)
            continue
        if in_fence:
            out.append(line)
            continue
        new_line = transform_line(line, renames, threshold, delta, filename)
        if new_line != line:
            changed += 1
        out.append(new_line)
    return "".join(out), changed


def build_plan(threshold, delta):
    """Compute the file rename map and validate it produces no collisions."""
    renames = {}
    for path in content_files():
        number = int(path.name[:2])
        if number >= threshold:
            new_number = number + delta
            if new_number < 0:
                sys.exit(f"error: section {number} would become {new_number}")
            new_name = f"{new_number:02d}-{path.name[3:]}"
            renames[path.name] = new_name

    # Detect collisions among the new names.
    seen = {}
    for old, new in renames.items():
        if new in seen:
            sys.exit(
                f"error: '{old}' and '{seen[new]}' would both become '{new}'"
            )
        seen[new] = old

    # A rename target may only collide with an existing file if that file is
    # itself being renamed away (handled by ordering below).
    for old, new in renames.items():
        existing = ROOT / new
        if existing.exists() and new not in renames:
            sys.exit(f"error: target '{new}' already exists")

    return renames


def apply_renames(renames, delta, dry_run):
    """Rename files in an order that avoids clobbering (stack-like shifts)."""
    ordered = sorted(
        renames.items(),
        key=lambda item: int(item[0][:2]),
        reverse=delta > 0,  # rename the highest number first when shifting up
    )
    for old, new in ordered:
        if dry_run:
            print(f"  rename {old} -> {new}")
        else:
            (ROOT / old).rename(ROOT / new)
            print(f"  renamed {old} -> {new}")


def cmd_shift(threshold, delta, dry_run):
    renames = build_plan(threshold, delta)
    verb = "insert-at" if delta > 0 else "remove-at"
    print(f"{verb} {threshold} (delta {delta:+d}):")
    apply_renames(renames, delta, dry_run)

    for path in walk_targets():
        text = path.read_text(encoding="utf-8")
        new_text, changed = transform_text(
            text, renames, threshold, delta, path.name
        )
        if changed or new_text != text:
            rel = path.name
            if dry_run:
                print(f"  would edit {rel}: {changed} line(s)")
            else:
                path.write_text(new_text, encoding="utf-8")
                print(f"  edited {rel}: {changed} line(s)")

    if delta < 0:
        leftovers = list(ROOT.glob(f"{threshold:02d}-*.md"))
        if leftovers:
            print()
            print(
                f"  note: {leftovers[0].name} is now an orphaned section file;"
                " delete it or merge its content by hand."
            )

    if dry_run:
        print("(dry run: nothing written)")
    else:
        print()
        print(
            "next steps:"
            " create/edit the section files as needed, then rebuild the"
            " combined reference with:"
            f"  .venv/bin/python build_reference.py"
        )


# ----------------------------------------------------------------------
# check mode
# ----------------------------------------------------------------------

LINK = re.compile(r"\]\(([^)]+\.md)\)")


def split_fences(text):
    """Yield (line, in_fence) pairs."""
    in_fence = False
    for line in text.splitlines():
        if FENCE.match(line):
            in_fence = not in_fence
            yield line, True
            continue
        yield line, in_fence


def cmd_check():
    problems = []

    files = {p.name: p for p in walk_targets()}
    content = {p.name: p for p in content_files()}

    # 1. Filenames parse and have unique numbers.
    numbers = {}
    for name in content:
        m = CONTENT_NAME.match(name)
        number = int(m.group(1))
        if number in numbers:
            problems.append(f"duplicate section number {number}: "
                            f"{numbers[number]} and {name}")
        numbers[number] = name

    # 2. Read all files once (fence-aware line lists).
    lines = {}
    for name, path in files.items():
        text = path.read_text(encoding="utf-8")
        lines[name] = list(split_fences(text))

    # 3. Every markdown link resolves to an existing file (a section file,
    #    or a real path outside the set such as ../compiler_docs.md).
    for name, pairs in lines.items():
        for line, in_fence in pairs:
            if in_fence:
                continue
            for target in LINK.findall(line):
                if target not in files and not (ROOT / target).exists():
                    problems.append(f"{name}: broken link target '{target}'")

    # 4. Content files carry a numbered H1 that matches the filename number.
    for name in content:
        number = int(CONTENT_NAME.match(name).group(1))
        h1 = None
        for line, in_fence in lines[name]:
            if in_fence:
                continue
            if line.startswith("# "):
                h1 = line
                break
        if h1 is None:
            problems.append(f"{name}: no '# ' heading found")
            continue
        m = re.match(r"^#\s+(\d+)\.\s+\S", h1)
        if m is None:
            if number != 0:
                problems.append(
                    f"{name}: heading '{h1.strip()}' has no section number"
                )
            continue
        if int(m.group(1)) != number:
            problems.append(
                f"{name}: heading says section {m.group(1)} "
                f"but filename says {number}"
            )

    # 5. SUMMARY.md drives the build; it must list existing files in order.
    summary_entries = []
    for line, in_fence in lines[NAV_SUMMARY]:
        if in_fence:
            continue
        m = re.match(r"^-\s+\[(.*?)\]\(([^)]+\.md)\)\s*$", line)
        if m:
            summary_entries.append((m.group(1), m.group(2)))

    if not summary_entries:
        problems.append(f"{NAV_SUMMARY}: no '- [title](file.md)' entries found")

    numbered = []
    for title, target in summary_entries:
        if target not in files:
            problems.append(f"{NAV_SUMMARY}: lists missing file '{target}'")
            continue
        m = re.match(r"^(\d+)\.\s", title)
        numbered.append((int(m.group(1)) if m else None, target))

    expected = 1
    for num, target in numbered:
        if num is None:
            continue  # the unnumbered 00 entry
        if num != expected:
            problems.append(
                f"{NAV_SUMMARY}: entry '{target}' is numbered {num}, "
                f"expected {expected}"
            )
        expected = num + 1

    # 6. Subsection headings inside each file: NN.1, NN.2, ... in order.
    for name in content:
        number = int(CONTENT_NAME.match(name).group(1))
        if number == 0:
            continue
        sub = None
        for line, in_fence in lines[name]:
            if in_fence:
                continue
            m = re.match(r"^##\s+(\d+)\.(\d+)\s+\S", line)
            if not m:
                if re.match(r"^##\s+\d+\.\d+\S", line):
                    problems.append(
                        f"{name}: malformed subsection heading "
                        f"(missing space): '{line.strip()}'"
                    )
                continue
            if int(m.group(1)) != number:
                problems.append(
                    f"{name}: subsection '{line.strip()}' does not start "
                    f"with {number}."
                )
            value = int(m.group(2))
            if sub is not None and value != sub + 1:
                problems.append(
                    f"{name}: subsection numbering jumps from "
                    f"{number}.{sub} to {number}.{value}"
                )
            sub = value

    # 7. 00-title-and-toc.md must mirror SUMMARY.md.
    toc_entries = []
    for line, in_fence in lines[NAV_TOC]:
        if in_fence:
            continue
        m = re.match(r"^\s*(\d+)\.\s+\[(.*?)\]\(([^)]+\.md)\)\s*$", line)
        if m:
            toc_entries.append((int(m.group(1)), m.group(3)))

    toc_pairs = {(num, target) for num, target in toc_entries}
    summary_pairs = {
        (num, target) for num, target in numbered if num is not None
    }
    if toc_pairs != summary_pairs:
        missing = summary_pairs - toc_pairs
        extra = toc_pairs - summary_pairs
        if missing:
            problems.append(
                f"{NAV_TOC}: missing entries {sorted(missing)}"
            )
        if extra:
            problems.append(f"{NAV_TOC}: unexpected entries {sorted(extra)}")
    if len(toc_entries) != len(set(n for n, _ in toc_entries)):
        problems.append(f"{NAV_TOC}: duplicate section numbers")

    # 8. README.md assembly order must match SUMMARY.md numbering.
    readme_numbers = []
    for line, in_fence in lines[NAV_README]:
        if in_fence:
            continue
        m = re.match(r"^(\d+)\.\s+\S", line)
        if m:
            readme_numbers.append(int(m.group(1)))
    if readme_numbers and readme_numbers != list(
        range(1, len(readme_numbers) + 1)
    ):
        problems.append(
            f"{NAV_README}: ordered list is not consecutive from 1: "
            f"{readme_numbers}"
        )
    summary_numbered_count = sum(1 for num, _ in numbered if num is not None)
    if readme_numbers and len(readme_numbers) != summary_numbered_count:
        problems.append(
            f"{NAV_README}: lists {len(readme_numbers)} sections but "
            f"{NAV_SUMMARY} lists {summary_numbered_count}"
        )

    if problems:
        print(f"check: {len(problems)} problem(s):")
        for problem in problems:
            print(f"  - {problem}")
        sys.exit(1)

    print(
        f"check: OK — {len(content)} content files, "
        f"{len(summary_entries)} SUMMARY entries, "
        "all links resolve, numbering consistent"
    )


def main():
    parser = argparse.ArgumentParser(
        description="Propagate section renumbering across the split docs."
    )
    sub = parser.add_subparsers(dest="command", required=True)

    p_insert = sub.add_parser(
        "insert-at", help="shift all sections >= N up by one (make room)"
    )
    p_insert.add_argument("n", type=int)

    p_remove = sub.add_parser(
        "remove-at", help="shift all sections > N down by one (close gap)"
    )
    p_remove.add_argument("n", type=int)

    for p in (p_insert, p_remove):
        p.add_argument(
            "-n", "--dry-run", action="store_true",
            help="show what would change without writing",
        )

    sub.add_parser("check", help="validate numbering and links")

    args = parser.parse_args()

    if args.command == "check":
        cmd_check()
    elif args.command == "insert-at":
        cmd_shift(args.n, +1, args.dry_run)
    elif args.command == "remove-at":
        cmd_shift(args.n + 1, -1, args.dry_run)


if __name__ == "__main__":
    main()
