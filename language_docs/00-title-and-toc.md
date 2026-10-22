# Franko Language Reference

This document describes the Franko language as implemented by the current compiler pipeline:

```text
Franko source
  -> lexer + parser
  -> parser AST
  -> Desugarer
  -> SemanticAnalyzer
  -> LegalityChecker (declarations, expressions, statements, functions, types)
  -> MemorySafetyChecker
  -> C++14 code generation
```

It documents both intended language behavior and important current implementation restrictions.

---

## Table of Contents

1. [Overview](01-overview.md)
2. [Source Files](02-source-files.md)
3. [Program Structure](03-program-structure.md)
4. [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md)
5. [Desugaring](05-desugaring.md)
6. [Types](06-types.md)
7. [Primitive Integer Types](07-primitive-integer-types.md)
8. [Integer Literals](08-integer-literals.md)
9. [Fluid Integer Constants](09-fluid-integer-constants.md)
10. [Constant Folding](10-constant-folding.md)
11. [Declarations](11-declarations.md)
12. [Functions](12-functions.md)
13. [Heap Variables and `alloc`](13-heap-variables-and-alloc.md)
14. [Assignment](14-assignment.md)
15. [Lvalues and Storage-Backed Expressions](15-lvalues-and-storage-backed-expressions.md)
16. [Operators](16-operators.md)
17. [Conditions](17-conditions.md)
18. [Statements](18-statements.md)
19. [Arrays](19-arrays.md)
20. [Array Intrinsics](20-array-intrinsics.md)
21. [Array Initializer Lists](21-array-initializer-lists.md)
22. [String Literals](22-string-literals.md)
23. [Addresses](23-addresses.md)
24. [`del` and Delete Checking](24-del-and-delete-checking.md)
25. [Memory Safety Checking](25-memory-safety-checking.md)
26. [Type Equality](26-type-equality.md)
27. [Declaration Type Validity](27-declaration-type-validity.md)
28. [Unsupported or Restricted Features](28-unsupported-or-restricted-features.md)
29. [Runtime and Backend Notes](29-runtime-and-backend-notes.md)
30. [Examples](30-examples.md)