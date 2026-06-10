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
16. Operators
17. Conditions
18. Statements
19. Arrays
20. Array Intrinsics
21. Array Initializer Lists
22. Addresses
23. `del` and Delete Checking
24. Type Equality
25. Declaration Type Validity
26. Unsupported or Restricted Features
27. Runtime and Backend Notes
28. Examples

## Important cleanup applied

Your pasted reference accidentally duplicated the Arrays section:

- `# 19. Arrays` appeared as the original Arrays section.
- `# 20. Arrays` appeared again as a revised Arrays section.
- `# 22. Array Intrinsics` followed afterward.

This pack fixes that by using this cleaner layout:

- `19-arrays.md`
- `20-array-intrinsics.md`
- `21-array-initializer-lists.md`
- `22-addresses.md`
- ...
- `28-examples.md`

So **Examples is Section 28**, not Section 29.

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
