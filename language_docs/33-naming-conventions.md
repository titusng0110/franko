# 33. Naming Conventions

Franko does not require particular spellings for user-defined names, but
consistent names make programs easier to read and make the standard library
easier to navigate. The conventions in this chapter are the recommended style
for Franko code.

Only the private-member and namespace-internal access rules in §33.7 produce
compiler warnings. The other conventions in this chapter are style guidance.

## 33.1 Summary

| Item | Convention | Examples |
|---|---|---|
| Struct | `CapitalLetterCamelCase` | `BigInteger`, `Point`, `SparseSetAllocator` |
| Internal struct | `__` + `CapitalLetterCamelCase` | `__SecretStruct` |
| Function | `lower_case_snake_case` | `read_line`, `new_index` |
| Method | `lower_case_snake_case` | `write_active_indices`, `get_capacity` |
| Private method | `_` + `lower_case_snake_case` | `_secret_function` |
| Internal function | `__` + `lower_case_snake_case` | `__private_helper` |
| Variable | `lower_case_snake_case` | `byte_count`, `is_ready` |
| Field | `lower_case_snake_case` | `capacity`, `next_index` |
| Private field | `_` + `lower_case_snake_case` | `_secret_field`, `_capacity` |
| Namespace | short, lowercase, no underscores | `bignum`, `os`, `win32` |
| Constant | `ALL_CAPS_WITH_UNDERSCORES` | `IS_WINDOWS`, `STDOUT_FILENO` |
| Private member constant | `_` + `ALL_CAPS_WITH_UNDERSCORES` | `_END`, `_ALLOCATED` |
| Internal constant | `__` + `ALL_CAPS_WITH_UNDERSCORES` | `__SECRET_CONSTANT` |

## 33.2 Structs

Struct names use `CapitalLetterCamelCase`:

```franko
struct BigInteger {
    int32 value
}

struct SparseSetAllocator {
    [uint32] _sparse
    [uint32] _dense
}
```

A struct that is internal to its namespace keeps the same base spelling after
the two-underscore prefix:

```franko
namespace bignum {
    struct __SecretStruct {
        int32 value
    }
}
```

## 33.3 Functions and Methods

Function and method names use `lower_case_snake_case`:

```franko
func read_line(view<[byte]> buffer) -> uint32 {
    return 0
}

struct Counter {
    int32 count = 0

    func add_value(view<Counter> self, int32 value) -> void {
        return
    }
}
```

A private method keeps the same base spelling after the single-underscore
prefix, and an internal function keeps it after the two-underscore prefix:

```franko
struct Counter {
    int32 count = 0

    func _secret_function(view<Counter> self) -> int32 {
        return $(self).count
    }
}

func __private_helper() -> int32 {
    return 2
}
```

Direct foreign-function mappings are the exception: the function name
preserves the platform API spelling, even when that is not snake case. The
namespace remains lowercase and underscore-free:

```franko
os::win32::GetStdHandle(os::win32::STD_OUTPUT_HANDLE)
os::win32::ReadFile(...)
os::win32::WriteFile(...)
```

This exception exists only for direct FFI mappings such as the Win32 file
API: a mapping like `os::win32::ReadHandle()` keeps the Windows spelling even
though an ordinary Franko function would be written `read_handle`. Ordinary
Franko functions and methods still use snake case.

Built-in names are fixed and are not renamed. Array intrinsics (`memcpy`,
`memset`, `length`, `read_uint16_LE`, ...) and pointer intrinsics (`getptr`,
`getmview`, `getview`) keep their existing spelling.

Operator methods are the other fixed-name exception: `OPERATOR_EQ`,
`OPERATOR_MOV`, and the other reserved `OPERATOR_*` names are exact,
uppercase, and not renamed to snake case. They are reserved for struct
operator methods and rejected on free functions. See
[Operator Methods](26-methods.md#2610-operator-methods).

## 33.4 Variables and Fields

Variable and field names use `lower_case_snake_case`:

```franko
int32 byte_count = 0
uint32 is_ready = 1

struct Buffer {
    [byte] data
    uint32 capacity = 0
}
```

A private field keeps the same base spelling after the single-underscore
prefix:

```franko
struct Buffer {
    [byte] data
    uint32 _capacity = 0
}
```

## 33.5 Namespaces

Namespace names should be short, lowercase, and free of underscores. When a
name really needs two words, concatenate them instead of inserting an
underscore:

```franko
namespace bignum {
    struct BigInteger {
        int32 value
    }
}

namespace os::win32 {
}
```

Use `bignum::BigInteger` and `os::win32`, not `big_num::BigInteger` or
`os::win_32`.

## 33.6 Constants

Values intended to be constants use `ALL_CAPS_WITH_UNDERSCORES`:

```franko
byte IS_WINDOWS = 0
int32 STDOUT_FILENO = 1
```

A constant that belongs to a private member scope uses the single-underscore
prefix:

```franko
int32 _END = 0xFFFFFFFF
int32 _ALLOCATED = 0xFFFFFFFE
```

An internal constant adds the two-underscore prefix:

```franko
int32 __SECRET_CONSTANT = 42
```

## 33.7 Private and Internal Prefixes

Underscore prefixes communicate visibility:

- one leading underscore `_` marks a private struct member: `_secret_field`,
  `_secret_function`, `_capacity`, `_END`;
- two leading underscores `__` mark a namespace-internal declaration:
  `__private_helper`, `__SecretStruct`, `__SECRET_CONSTANT`.

The prefix composes with the base convention for the kind of declaration. For
example, a private field uses snake case after `_`, while an internal struct
uses CamelCase after `__`.

Tuple element names are declaration-site aliases, not symbols: `(int32 val,
int32 err)` and `(int32 left, int32 right)` are the same tuple type, and the
names are only used for member access in their own declaration. Unnamed tuple
elements are accessed as `_0`, `_1`, and so on. These names are not struct
members, so the `_` private-field warning does not apply to them.

Compiler-generated tuple structs use the C++ type name
`_TMP_STRUCT_<hash>`. Ordinary user structs always emit with the `STRUCT_`
prefix (`STRUCT_<Name>`), so a user struct whose Franko name begins with
`_TMP_STRUCT_` still emits a distinct C++ name and is allowed.

The compiler currently warns about the following public accesses:

- accessing a field or method whose name starts with `_` from outside its
  declaring struct;
- calling a free function or naming a struct whose name starts with `__` from
  outside its declaring namespace.

See [Lexical Scoping and Symbols](04-lexical-scoping-and-symbols.md) for the
exact access rules. These warnings do not reject the program. Other naming
conventions in this chapter are not enforced by the compiler.

## 33.8 Standard Library Imports

The compiler's bundled standard library is stored under `./import/franko/`.
The import prefix `franko/` is reserved for those standard modules:

```franko
import "franko/io"
import "franko/os"
import "franko/pool"
```

User modules must not be placed under the compiler's `import/franko/`
directory and must not use the `franko/` import prefix. Put user modules in
your own directory or a project-relative path instead:

```franko
import "./lib/my_module"
import "myproject/my_module"
```

See [Source Files](02-source-files.md) for the import search rules.

## 33.9 Compiler-Enforced Rules

The naming conventions are mostly documentation and style. The compiler only
enforces the access restrictions for the reserved prefixes:

- `_`-prefixed fields and methods are private to their struct;
- `__`-prefixed free functions and structs are internal to their namespace.

The struct casing, function and variable casing, namespace spelling, constant
casing, and `franko/` import reservation are conventions that should be
followed by new code but are not currently diagnosed by the compiler.
