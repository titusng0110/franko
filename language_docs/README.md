# Franko Language Reference — Split Markdown Pack

This pack restructures the Franko language reference into smaller Markdown files.

## Recommended assembly order

Use `SUMMARY.md` as the canonical order.

The cleaned section ordering is:

1. Overview
2. Source Files
3. Program Structure
4. Lexical Scoping and Symbols
5. Desugaring
6. Types
7. Primitive Integer Types
8. Integer Literals
9. Fluid Integer Constants
10. Constant Folding
11. Declarations
12. Functions
13. Heap Variables and `alloc`
14. Assignment
15. Lvalues and Storage-Backed Expressions
16. Casts
17. Operators
18. Conditions
19. Statements
20. Arrays
21. Array Intrinsics
22. Array Initializer Lists
23. String Literals
24. Addresses
25. Structs
26. Methods
27. `del` and Delete Checking
28. Memory Safety Checking
29. Type Equality
30. Declaration Type Validity
31. Unsupported or Restricted Features
32. Runtime and Backend Notes
33. Foreign Function Interface
34. Examples

## How to rebuild one combined document

Run:

```bash
python3 build_reference.py
```

This creates:

```text
Franko_Language_Reference.md
```

from the split files in `SUMMARY.md` order.

## Tooling

The doc scripts live in this directory and are meant to run inside a local
uv-managed virtual environment (git-ignored):

```bash
uv venv                 # creates .venv/
uv pip install regex    # used by renumber_sections.py
```

### Renumbering sections

Section numbers are embedded in filenames (`25-structs.md`), headings
(`# 24. Structs`, `## 24.1 ...`), cross-links, and the three navigation files
(`SUMMARY.md`, `00-title-and-toc.md`, `README.md`). Inserting or removing a
chapter means shifting every later number. `renumber_sections.py` propagates
that shift in one step:

```bash
.venv/bin/python renumber_sections.py insert-at 24 -n  # preview (dry run)
.venv/bin/python renumber_sections.py insert-at 24     # make room at 24
# ...create/edit the new 24-*.md, then update SUMMARY/TOC/README entries...
.venv/bin/python renumber_sections.py check            # validate numbering + links
.venv/bin/python build_reference.py                    # regenerate the combined doc
```

`insert-at N` shifts every section `>= N` up by one (files, headings, links,
navigation entries). `remove-at N` shifts every section `> N` down by one and
prints a reminder to delete the orphaned `N-*.md` by hand. Fenced code blocks
are never touched, and `Franko_Language_Reference.md` is never edited (always
regenerate it with `build_reference.py` afterwards).

### Consistency check

```bash
.venv/bin/python renumber_sections.py check
```

validates that: every SUMMARY link target exists, section numbers are
consecutive from 1, each file's `# NN.` heading matches its filename number,
subsections are sequential (`NN.1`, `NN.2`, ...), the TOC in
`00-title-and-toc.md` mirrors `SUMMARY.md`, the README order matches, and no
markdown link is broken.