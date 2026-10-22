# 25. `del` and Delete Checking

The semantic model distinguishes between heap and non-heap variables.

Only heap variables may be deleted.

Delete syntax:

```franko
del name;
```

The `del` operand is a variable name, not a general expression.

Valid, assuming `x` is declared as a heap variable:

```franko
alloc int x;
del x;
```

Non-heap variables cannot be deleted.

Double delete is invalid.

After deletion, direct use of the variable is invalid.

Dynamic array `uninit()` is separate from `del`. `uninit()` frees a heap or stack dynamic array's element buffer; `del` frees a heap-allocated variable object (and, for a heap dynamic array, also its element buffer through the object destructor).

Checking of double delete, use-after-delete, and leaks is performed by the dedicated memory safety pass. See [Memory Safety Checking](26-memory-safety-checking.md).

## 25.1 Delete Checking Limitations

Current limitations:

- delete checking is symbol-based;
- aliasing through addresses is not fully tracked;
- dangling addresses are not fully detected;
- delete state checking is branch-sensitive up to a nesting-depth limit, then path-insensitive;
- deleting in one branch may affect later checking conservatively.

The compiler does not perform full ownership, lifetime, alias, or dangling-address analysis.