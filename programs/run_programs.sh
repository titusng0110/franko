#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================
# Franko program smoke test
# ============================================================
#
# Compiles every program in programs/, runs it, and compares its
# stdout against the checked-in <program>.expected file.
#
# Every program in programs/ is a positive test. Negative checker
# tests live in tests/ and are intentionally not part of this
# smoke test; run ./tests/run_specs_tests.sh for the full suite.
#
# Success golden outputs must be deterministic. Never put values
# whose rendering varies across runs or platforms (such as live
# addresses) in a .expected file.
#
# A program may provide programs/<name>.args: each line is one
# command-line argument passed verbatim to the binary.
#
# A program may provide programs/<name>.stdin: bytes fed to the binary
# on stdin (carriage returns are stripped, so CRLF checkouts behave
# like LF ones). This is what lets interactive-style programs that
# read stdin run deterministically under the smoke test.
#
# Generated artifacts (.cpp, .testbin, .actual) are removed after a
# passing case and kept for inspection after a failure.
#
# Usage:
#   ./programs/run_programs.sh
# ============================================================

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# The Franko compiler reads ProgramTemplate.cpp relative to the cwd.
CP=".:lib/antlr-4.13.2-complete.jar:build/generated:build/classes"
JEM_INC="third_party/jemalloc/linux-x86_64/include"
JEM_LIB="third_party/jemalloc/linux-x86_64/lib/libjemalloc.a"

# Ensure compiled Java compiler classes exist.
if [[ ! -f "$ROOT/build/classes/Main.class" ]]; then
    echo "Error: compiler toolchain not built: $ROOT/build/classes/Main.class" >&2
    echo "Run ./update_linux-x86_64.sh first." >&2
    exit 1
fi

# The import search directory is passed explicitly so programs that
# import library modules (e.g. programs/stack.fr importing
# "franko/vector") resolve the same way regardless of cwd.
IMPORT_DIR="$ROOT/import"

PASSED=0
FAILED=0

fail() {
    echo "FAIL: $1"
    FAILED=$((FAILED+1))
}

pass() {
    echo "PASS: $1"
    PASSED=$((PASSED+1))
}

shopt -s nullglob
for SRC in programs/*.fr; do
    BASE="${SRC%.fr}"
    OUT_CPP="$BASE.cpp"
    OUT_BIN="$BASE.testbin"
    ACTUAL="$BASE.actual"
    EXPECTED="$BASE.expected"
    ARGS_FILE="$BASE.args"
    STDIN_FILE="$BASE.stdin"

    echo
    echo "=== $SRC ==="
    echo

    # Optional per-program CLI arguments: each line of
    # programs/<name>.args is one argument passed verbatim to the
    # binary (empty lines pass empty-string arguments; a trailing
    # newline does not add an argument).
    RUN_ARGS=()

    if [[ -f "$ARGS_FILE" ]]; then
        while IFS= read -r LINE || [[ -n "$LINE" ]]; do
            LINE="${LINE%$'\r'}"
            RUN_ARGS+=("$LINE")
        done < "$ARGS_FILE"
    fi

    if ! java -cp "$CP" Main "$SRC" -o "$OUT_CPP" -p "$IMPORT_DIR"; then
        fail "$SRC (Franko compile failed)"
        continue
    fi

    if ! g++ \
        -O3 -std=c++14 -Wall -Wextra -Wpedantic \
        -Iinclude -I"$JEM_INC" \
        "$OUT_CPP" "$JEM_LIB" \
        -pthread -ldl -static-libstdc++ -static-libgcc \
        -o "$OUT_BIN"; then
        fail "$SRC (C++ compile failed)"
        continue
    fi

    # Optional piped stdin: bytes from programs/<name>.stdin feed the
    # binary when the file exists. With `set -o pipefail` the pipeline
    # status is the binary's exit status.
    if [[ -f "$STDIN_FILE" ]]; then
        RUN_OK=0
        tr -d '\r' < "$STDIN_FILE" | "$OUT_BIN" ${RUN_ARGS[@]+"${RUN_ARGS[@]}"} > "$ACTUAL" 2>&1 || RUN_OK=$?
    else
        RUN_OK=0
        "$OUT_BIN" ${RUN_ARGS[@]+"${RUN_ARGS[@]}"} > "$ACTUAL" 2>&1 || RUN_OK=$?
    fi

    if [[ "$RUN_OK" -ne 0 ]]; then
        echo "---- runtime output: ----"
        cat "$ACTUAL"
        fail "$SRC (binary exited non-zero)"
        continue
    fi

    echo "Comparing output against $EXPECTED:"
    if ! diff -u "$EXPECTED" "$ACTUAL"; then
        fail "$SRC (output mismatch)"
        continue
    fi

    echo
    echo "PASS: $SRC ran successfully, output matches $EXPECTED"
    rm -f "$OUT_CPP" "$OUT_BIN" "$ACTUAL"
    PASSED=$((PASSED+1))
done

echo
echo "passed: $PASSED  failed: $FAILED"

if [[ "$FAILED" -ne 0 ]]; then
    exit 1
fi

echo "✅ All programs passed."
