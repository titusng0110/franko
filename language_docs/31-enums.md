# 31. Enums

An enum is a nominal type backed by one of the eight primitive integer
types. Enums model logical states, not raw numbers: they support
equality, ordering, and assignment, but no arithmetic or bitwise
operations and no implicit conversions to or from integers.

```franko
enum<uint8> AiState {
    sleeping,
    hunting,
    attacking,
    dead
}
```

Enums are top-level items, valid at global scope or inside a namespace.
They are not valid inside structs. See [Program Structure](03-program-structure.md).

## 31.1 Declaration Syntax

Form 1 — all explicit values:

```franko
enum<int32> Types {
    type1 = 1,
    type2 = 10,
    type3 = 20
}
```

Form 2 — implicit auto-increment:

```franko
enum<int32> States {
    state1,   // 0
    state2,   // 1
    state3    // 2
}
```

Rules:

- The backing type is required and must be one of the eight primitive
  integer types. Arrays, pointers, and structs are invalid.
- Every variant identifier must be unique.
- Every variant must evaluate to a strictly unique underlying integer
  value.
- An enum must either provide an explicit `= constExpr` for **every**
  variant or for **none**; mixing explicit and implicit values is a
  compile error.
- Explicit values must be compile-time constant expressions and must
  fit the declared backing type.
- With no explicit values, the first variant is `0` and each subsequent
  variant increments the previous value by `1`.
- Implicit auto-increment wraps using standard two's-complement
  arithmetic when it exceeds the backing type's maximum:
  - signed: `2147483647 + 1` wraps to `-2147483648` and remains legal
    if unique;
  - unsigned: `255 + 1` wraps to `0`, which is a duplicate if `0` was
    already used.
- Variants are accessed through namespace resolution on the enum type
  name:

```franko
Types::type1
States::state2
```

## 31.2 Default Value

The first declared variant is the enum's absolute default value. Enums
never start as uninitialized or indeterminate memory. An enum variable may be
`const` (`const Status s = Status::alive` or `const Status s`), because enum
types are value-assignable; the const rules then forbid later assignment and
mutable pointer creation. See
[Declarations](18-declarations.md#188-const-storage).

- Local variables: `Types t;` initializes to the first variant.
- Global variables: `Types g;` initializes to the first variant before
  `main` runs, even when the backing value is not `0`.
- Struct fields: a struct containing an enum gains a default value for
  that field.
- Dynamic arrays: `arr.init(n)` and `arr.init_zero(n)` populate every
  element with the first variant.
- `init_zero` returns byte-zeroed memory, but explicitly
  default-constructs elements whose type has a default value. The zero
  bytes are safely overwritten with the first variant.

Valid:

```franko
enum<uint8> Status {
    spawning = 10,  // this is the default value
    alive = 20
}

struct Entity {
    int32 id
    Status state    // defaults to Status::spawning
}

func main() -> int32 {
    Entity e;
    stdout(e.state == Status::spawning); // 1 (true)
    return 0;
}
```

## 31.3 Operators

Enums are logical states, not raw numbers.

Allowed:

- equality: `==`, `!=`
- ordering: `<`, `>`, `<=`, `>=` (using backing integer values)
- assignment: `=`

Both operands in a comparison or assignment must have the exact same
enum type. An enum cannot be compared to an integer or to a different
enum type.

Disallowed:

- arithmetic: `+`, `-`, `*`, `/`, `%`
- bitwise: `&`, `|`, `^`, `~`, `<<`, `>>`
- compound assignment: `+=`, `-=`, etc.

Invalid:

```franko
Status s = Status::spawning;
s = s + 1;               // arithmetic is banned
if (s == 0) { ... }      // cannot compare enum to int
```

## 31.4 Type Identity and Casting

Enums are nominal. Two enums with the same backing type and variants
are distinct and incompatible. There are no implicit conversions
between enums and integers. See [Type Equality](10-type-equality.md).

Explicit casts transition between enums and raw integers. Enums obey
the strict split between statically safe `cast<T>` and
runtime-checked `unsafe_cast<T>` (see [Casts](12-casts.md)).

**`cast<T>` — value-preserving / safe**

Allowed from an enum to its exact backing type, or to a strictly wider
compatible integer type:

```franko
enum<uint8> State { a, b }
uint8 exact = cast<uint8>(State::b);     // valid: exact backing type
int32 wider = cast<int32>(State::b);     // valid: value-preserving widening
```

**`unsafe_cast<T>` — checked / can lose data or abort**

A superset of `cast`. It also permits:

- **Integer to enum:** the integer must match a declared variant;
  otherwise the program aborts at runtime.
- **Enum to a narrower or signedness-incompatible integer:** the value
  must fit the target type; otherwise the program aborts at runtime.

```franko
enum<uint32> BigState { a = 0, b = 300 }

uint8 narrow = unsafe_cast<uint8>(BigState::a); // succeeds (0 fits)
uint8 crash = unsafe_cast<uint8>(BigState::b);  // aborts (300 does not fit uint8)

BigState s1 = unsafe_cast<BigState>(0);         // succeeds (maps to a)
BigState s2 = unsafe_cast<BigState>(999);       // aborts (999 is not a variant)
```

Enum-related constant casts are intentionally runtime-checked. They are
not rejected at compile time merely because the literal is known to be
invalid.

## 31.5 Memory Safety Constraints

Enums participate in array intrinsic checking:

- `#is_memset_safe(x)` is `0` for enums. `memset` cannot be called on
  an enum array.
- A struct or tuple containing an enum is not memset-safe.
- `#is_memcpy_safe(x)` is `1`. Copying already-valid enums
  byte-for-byte is safe.

Invalid:

```franko
[Status, 10] states;
states.memset(0);    // invalid: Status is not memset-safe
```

Valid:

```franko
[Status, 3] states;
states.memcpy([Status::alive, Status::alive, Status::alive]); // valid
```

Array literals may therefore use enum element types when their elements
are enum variant references (see [Array Literals](22-array-literals.md)).

## 31.6 Compile-Time Reflection

| Predicate | Result | Reason |
|---|---|---|
| `#is_primitive` | `0` | Enums are distinct nominal types, not primitives. |
| `#is_enum` | `1` | The type is an enum. |
| `#has_default_value` | `1` | The first variant is always applied. |
| `#is_memset_safe` | `0` | Banned to prevent invalid state-machine tags. |
| `#is_memcpy_safe` | `1` | Safe to copy valid types. |
| `#is_assignable` | `1` | `=` is fully supported. |
| `#is_value_assignable` | `1` | Enums can be passed and returned by value. |
| `#is_matchable` | `1` | Built-in `==` and `!=` are supported. |
| `#is_comparable` | `1` | Built-in `<`, `>`, `<=`, `>=` are supported. |
| `#what_type` | string | The enum's nominal spelling, e.g. `"Status"`. |

`#is_enum` accepts a type or an identifier/value, like the other type
predicates, and returns `uint8`. See
[Compile-Time Reflection](35-compile-time-reflection.md).

## 31.7 Complete Example: Data-Oriented State Machine

Franko avoids OOP. Behavior is driven by data classes and strict enum
state machines. This example uses the implicit auto-increment form.

```franko
import "franko/stdio"

// Auto-increments: sleeping=0, hunting=1, attacking=2, dead=3
enum<uint8> AiState {
    sleeping,
    hunting,
    attacking,
    dead
}

struct Enemy {
    int32 id
    int32 health = 100
    AiState state    // Auto-defaults to AiState::sleeping (0)
}

// Systems process data in bulk based on enum state
func update_enemies(mview<[Enemy]> enemies) -> void {
    for (uint32 i = 0; i < $(enemies).length(); i++) {

        if ($(enemies)[i].state == AiState::sleeping) {
            // Wake up logic
            $(enemies)[i].state = AiState::hunting;
        }
        else if ($(enemies)[i].state == AiState::hunting) {
            // Chase logic
            if ($(enemies)[i].health < 50) {
                $(enemies)[i].state = AiState::attacking;
            }
        }
        else if ($(enemies)[i].state >= AiState::attacking) {
            // Handles attacking and dead states via ordering operator
            if ($(enemies)[i].health <= 0) {
                $(enemies)[i].state = AiState::dead;
            }
        }
    }
    return;
}

func main() -> int32 {
    [Enemy] mob_pool;
    mob_pool.init_zero(10); // Safe: zero-fills, then writes AiState::sleeping (0) to each entity

    update_enemies(getmview(mob_pool));

    stdout(mob_pool[0].state == AiState::hunting); // 1 (true)

    mob_pool.uninit();
    return 0;
}
```

## 31.8 Naming Convention

- Enum type names use `CapitalLetterCamelCase`:
  - `Status`
  - `AiState`
  - `NetworkState`
- Enum variants use `camelCaseStartingWithLowerCaseLetter`:
  - `Types::type1`
  - `Types::complexType`
  - `Status::pending`
  - `AiState::sleeping`
  - `AiState::attacking`

See [Naming Conventions](37-naming-conventions.md).
