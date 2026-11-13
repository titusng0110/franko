# Franko example programs

These programs are both readable examples and the `programs/` smoke-test
suite. The runner compiles every top-level `programs/*.fr`, runs it, and
compares stdout against the matching `.expected` file.

```bash
uv run python programs/run_programs.py
```

Each program is self-contained: there is no shared helper module. Text
parsing and formatting use `franko/string` (`std::String`), with small
local helpers embedded in the programs that need them. Token parsers use
`std::String::to_int64` / `to_uint32`; text output uses `std::String`
slices and `stdout`.

## Design notes

- The examples are data-oriented: plain structs, enums, parallel arrays,
  `uint32` indices, and pool allocators. Node-like structures (trie nodes,
  cache entries) use `franko/pool` instead of linked `ptr<Node>` chains.
- Ordinary struct methods are used where they make the code clearer; the
  examples do not build an inheritance or class hierarchy.
- Text handling uses `franko/string`: stdin lines are read into fixed byte
  buffers, then sliced into `std::String` tokens for parsing, matching,
  and printing. Raw byte arrays and array intrinsics are still used for
  binary formats and fixed-size input buffers.
- Output is ASCII, LF-only, deterministic, and identical on every platform.

## Programs

| Program | What it shows | Input |
|---|---|---|
| `greetings.fr` | stdin, `std::String` `from`/`+=`, line trimming | `.stdin` |
| `ledger.fr` | enums, arrays of account/transaction data, `Money` operator methods, `std::String` command parsing | `.stdin` |
| `wordfreq.fr` | pool-backed trie, `[uint32, 26]` links, `std::String` word parsing, DFS | `.stdin` |
| `calculator.fr` | tokenizer + recursive-descent parser, enums, tuples, `std::String` number parsing, error handling | `.stdin` |
| `toposort.fr` | index-linked adjacency arrays, DFS colors, cycle detection, `std::String` token parsing | `.stdin` |
| `dijkstra.fr` | weighted graph, array binary heap, enum node states, path reconstruction, `std::String` token parsing | `.stdin` |
| `lru_cache.fr` | `SparseSetAllocator` entries, open-addressed key table, index-linked LRU list, `std::String` command parsing | `.stdin` |
| `binary_codec.fr` | byte/bit/endian intrinsics, FNV-1a checksum, reflection, `#if` compile-time assertions | `.args` |
| `life.fr` | nested enum grids, double buffering with `memcpy` | none |
| `sudoku.fr` | nested static arrays, bitmask candidate elimination, recursive backtracking | `.stdin` |

## Input conventions

Most stdin programs use a count-prefixed format (`N`, then `N`
lines/commands) so the smoke test does not depend on EOF versus blank-line
heuristics. `sudoku.fr` reads exactly nine rows, and `greetings.fr` reads
one line. Fixtures are ASCII and fit the programs' fixed byte line
buffers.

## Generated artifacts

`programs/run_programs.py` keeps the generated `.cpp` (and `.asan.cpp`)
sources next to each program so the C++14 output can be inspected. They
are git-ignored; test binaries and captured `.actual` output are removed
after a passing case. Failed cases keep everything for inspection.
