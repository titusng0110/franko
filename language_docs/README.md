# Franko Language Reference — Split Markdown Pack

This pack restructures the Franko language reference into smaller Markdown files.

These documents describe the Franko language and its observable behavior. Compiler
architecture, internal representations, lowering passes, and generated C++ details
belong in `compiler_docs.md`, not here.

## Recommended assembly order

Use `SUMMARY.md` as the canonical order.

The cleaned section ordering is:

1. Overview
2. Source Files
3. Program Structure
4. Lexical Scoping and Symbols
5. Primitive Integer Types
6. Integer Literals
7. Fluid Integer Constants
8. Constant Folding
9. Types
10. Type Equality
11. Declaration Type Validity
12. Casts
13. Operators
14. Conditions
15. Lvalues and Storage-Backed Expressions
16. Assignment
17. Statements
18. Declarations
19. Functions
20. Generics
21. Arrays
22. Array Literals
23. Array Intrinsics
24. String Literals
25. Shorthands and Equivalences
26. Pointers
27. Structs
28. Methods
29. Tuples
30. Aggregate Literals
31. Enums
32. Memory Safety Checking
33. Pool Allocation
34. Foreign Function Interface
35. Compile-Time Reflection
36. Unsupported or Restricted Features
37. Naming Conventions
38. Examples

## How to rebuild one combined document

Run:

```bash
uv run python language_docs/build_reference.py
```

This creates:

```text
Franko_Language_Reference.md
```

from the split files in `SUMMARY.md` order.

## Tooling

The doc scripts live in this directory. Run them from the repository root
through `uv`; `renumber_sections.py` uses the `regex` package declared in
the root `pyproject.toml`, and `build_reference.py` is stdlib-only.

```bash
uv run python language_docs/renumber_sections.py check
uv run python language_docs/build_reference.py
```

### Renumbering sections

Section numbers are embedded in filenames (`27-structs.md`), headings
(`# 24. Structs`, `## 24.1 ...`), cross-links, and the three navigation files
(`SUMMARY.md`, `00-title-and-toc.md`, `README.md`). Inserting or removing a
chapter means shifting every later number. `renumber_sections.py` propagates
that shift in one step:

```bash
uv run python language_docs/renumber_sections.py insert-at 24 -n  # preview (dry run)
uv run python language_docs/renumber_sections.py insert-at 24     # make room at 24
# ...create/edit the new 24-*.md, then update SUMMARY/TOC/README entries...
uv run python language_docs/renumber_sections.py check            # validate numbering + links
uv run python language_docs/build_reference.py                    # regenerate the combined doc
```

`insert-at N` shifts every section `>= N` up by one (files, headings, links,
navigation entries). `remove-at N` shifts every section `> N` down by one and
prints a reminder to delete the orphaned `N-*.md` by hand. Fenced code blocks
are never touched, and `Franko_Language_Reference.md` is never edited (always
regenerate it with `build_reference.py` afterwards).

### Consistency check

```bash
uv run python language_docs/renumber_sections.py check
```

validates that: every SUMMARY link target exists, section numbers are
consecutive from 1, each file's `# NN.` heading matches its filename number,
subsections are sequential (`NN.1`, `NN.2`, ...), the TOC in
`00-title-and-toc.md` mirrors `SUMMARY.md`, the README order matches, and no
markdown link is broken.