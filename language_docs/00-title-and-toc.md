# Franko Language Reference

This document describes the Franko language as implemented by the current compiler pipeline:

```text
Franko source
  -> lexer + parser
  -> parser AST
  -> Desugarer
  -> SemanticAnalyzer
  -> LegalityChecker (declarations, expressions, statements, functions, types)
  -> PointerNullStateChecker
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
13. [Pool Allocation](13-pool-allocation.md)
14. [Assignment](14-assignment.md)
15. [Lvalues and Storage-Backed Expressions](15-lvalues-and-storage-backed-expressions.md)
16. [Casts](16-casts.md)
17. [Operators](17-operators.md)
18. [Conditions](18-conditions.md)
19. [Statements](19-statements.md)
20. [Arrays](20-arrays.md)
21. [Array Intrinsics](21-array-intrinsics.md)
22. [Array Initializer Lists](22-array-initializer-lists.md)
23. [String Literals](23-string-literals.md)
24. [Pointers](24-pointers.md)
25. [Structs](25-structs.md)
26. [Methods](26-methods.md)
27. [Memory Safety Checking](27-memory-safety-checking.md)
28. [Type Equality](28-type-equality.md)
29. [Declaration Type Validity](29-declaration-type-validity.md)
30. [Unsupported or Restricted Features](30-unsupported-or-restricted-features.md)
31. [Foreign Function Interface](31-foreign-function-interface.md)
32. [Examples](32-examples.md)