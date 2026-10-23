#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================
# Franko spec tests
# ============================================================
#
# Runs the full spec test suite:
#
#   tests/failure/<family>/<case>.fr
#     Must be REJECTED by the Franko compiler (non-zero exit).
#     Optional <case>.errors: every non-empty line (lines starting
#     with '#' are comments) must appear as a substring of the
#     compiler output, pinning the expected diagnostics.
#     Helper files may live deeper (tests/failure/<family>/lib/);
#     they are imported by a fixture and are not run standalone.
#
#   tests/success/<concept>/main.fr
#     Must compile, run, and produce stdout matching main.expected.
#     Optional main.warnings: every non-empty line must appear as a
#     substring of the compiler output, pinning expected warnings.
#
#   tests/runtime/<family>/<case>.fr
#     Must compile, but the binary must EXIT NON-ZERO when run. Used
#     for documented runtime aborts such as uninitialized dynamic
#     array access. Runtime messages are intentionally not pinned:
#     they may change without breaking the suite.
#
# Success golden outputs must be deterministic. Never put values whose
# rendering varies across runs or platforms (such as live addresses)
# in a .expected file; cover those through the runtime category, which
# is judged by exit status only.
#
# Generated artifacts (.cpp, .bin, .actual, compiler logs) are removed
# after a passing case and kept for inspection after a failure.
#
# Usage:
#   ./tests/run_specs_tests.sh
# ============================================================

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# The Franko compiler reads ProgramTemplate.cpp relative to the cwd.
CP=".:lib/antlr-4.13.2-complete.jar:build/generated:build/classes"
JEM_INC="third_party/jemalloc/linux-x86_64/include"
JEM_LIB="third_party/jemalloc/linux-x86_64/lib/libjemalloc.a"

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

# ------------------------------------------------------------
# Every non-empty, non-comment line of EXPECTED must appear as a
# substring of OBSERVED. Used for expected diagnostics (.errors) and
# expected warnings (.warnings).
# ------------------------------------------------------------
check_substrings() {
    local EXPECTED="$1" OBSERVED="$2" LINE
    while IFS= read -r LINE || [[ -n "$LINE" ]]; do
        [[ -z "$LINE" ]] && continue
        [[ "$LINE" == \#* ]] && continue
        if ! grep -qF -- "$LINE" "$OBSERVED"; then
            echo "expected text not found in output: $LINE"
            return 1
        fi
    done < "$EXPECTED"
    return 0
}

# ------------------------------------------------------------
# Optional per-case compiler flags.
#
# A success fixture may provide main.flags; failure and runtime
# fixtures may provide <case>.flags. Each non-empty, non-comment
# line is split on whitespace and appended to the compiler
# invocation, e.g.:
#
#   -p tests/success/import_search_dir/search
#
# ------------------------------------------------------------
read_flags() {
    local FLAGS_FILE="$1"
    EXTRA_FLAGS=()

    [[ -f "$FLAGS_FILE" ]] || return 0

    while IFS= read -r LINE || [[ -n "$LINE" ]]; do
        [[ -z "$LINE" ]] && continue
        [[ "$LINE" == \#* ]] && continue
        # Word-splitting is intentional: one line may hold several flags.
        # shellcheck disable=SC2206
        EXTRA_FLAGS+=($LINE)
    done < "$FLAGS_FILE"
}

# ------------------------------------------------------------
# Failure cases: the compiler must reject the program.
# ------------------------------------------------------------
shopt -s nullglob
for SRC in tests/failure/*/*.fr; do
    NAME="${SRC%.fr}"
    OUT_CPP="$NAME.cpp"
    LOG="$NAME.compile.log"
    ERRS="$NAME.errors"
    FLAGS="$NAME.flags"

    echo "=== $SRC ==="

    read_flags "$FLAGS"

    if java -cp "$CP" Main "$SRC" -o "$OUT_CPP" ${EXTRA_FLAGS[@]+"${EXTRA_FLAGS[@]}"} > "$LOG" 2>&1; then
        echo "---- compiler output (accepted a failure program): ----"
        cat "$LOG"
        rm -f "$OUT_CPP" "$LOG"
        fail "$SRC"
        continue
    fi

    if [[ -f "$ERRS" ]] && ! check_substrings "$ERRS" "$LOG"; then
        echo "---- compiler output (missing expected diagnostics): ----"
        cat "$LOG"
        fail "$SRC (missing expected diagnostics)"
        continue
    fi

    rm -f "$LOG" "$OUT_CPP"
    pass "$SRC"
done

# ------------------------------------------------------------
# Success cases: compile, run, compare stdout with main.expected.
# ------------------------------------------------------------
for SRC in tests/success/*/main.fr; do
    DIR="$(dirname "$SRC")"
    NAME="$DIR/main"
    OUT_CPP="$NAME.cpp"
    OUT_BIN="$NAME.bin"
    ACTUAL="$NAME.actual"
    LOG="$NAME.compile.log"
    EXPECTED="$NAME.expected"
    WARN="$NAME.warnings"
    FLAGS="$NAME.flags"

    echo "=== $SRC ==="

    read_flags "$FLAGS"

    if ! java -cp "$CP" Main "$SRC" -o "$OUT_CPP" ${EXTRA_FLAGS[@]+"${EXTRA_FLAGS[@]}"} > "$LOG" 2>&1; then
        echo "---- compiler output: ----"
        cat "$LOG"
        fail "$SRC (Franko compile failed)"
        continue
    fi

    if [[ -f "$WARN" ]] && ! check_substrings "$WARN" "$LOG"; then
        echo "---- compiler output (missing expected warnings): ----"
        cat "$LOG"
        fail "$SRC (missing expected warnings)"
        continue
    fi

    if ! g++ \
        -O3 -std=c++14 -Wall -Wextra -Wpedantic -Wshadow \
        -Iinclude -I"$JEM_INC" \
        "$OUT_CPP" "$JEM_LIB" \
        -pthread -ldl -static-libstdc++ -static-libgcc \
        -o "$OUT_BIN" > "$LOG" 2>&1; then
        echo "---- g++ output: ----"
        cat "$LOG"
        fail "$SRC (C++ compile failed)"
        continue
    fi

    if ! "$OUT_BIN" > "$ACTUAL" 2>&1; then
        echo "---- runtime output: ----"
        cat "$ACTUAL"
        fail "$SRC (binary exited non-zero)"
        continue
    fi

    if ! diff -u "$EXPECTED" "$ACTUAL"; then
        fail "$SRC (output mismatch)"
        continue
    fi

    rm -f "$OUT_CPP" "$OUT_BIN" "$LOG" "$ACTUAL"
    pass "$SRC"
done

# ------------------------------------------------------------
# Runtime cases: the program compiles but must abort at run time.
# ------------------------------------------------------------
for SRC in tests/runtime/*/*.fr; do
    NAME="${SRC%.fr}"
    OUT_CPP="$NAME.cpp"
    OUT_BIN="$NAME.bin"
    ACTUAL="$NAME.actual"
    LOG="$NAME.compile.log"
    FLAGS="$NAME.flags"

    echo "=== $SRC ==="

    read_flags "$FLAGS"

    if ! java -cp "$CP" Main "$SRC" -o "$OUT_CPP" ${EXTRA_FLAGS[@]+"${EXTRA_FLAGS[@]}"} > "$LOG" 2>&1; then
        echo "---- compiler output: ----"
        cat "$LOG"
        fail "$SRC (Franko compile failed; runtime cases must compile)"
        continue
    fi

    if ! g++ \
        -O3 -std=c++14 -Iinclude -I"$JEM_INC" \
        "$OUT_CPP" "$JEM_LIB" \
        -pthread -ldl -static-libstdc++ -static-libgcc \
        -o "$OUT_BIN" > "$LOG" 2>&1; then
        echo "---- g++ output: ----"
        cat "$LOG"
        fail "$SRC (C++ compile failed)"
        continue
    fi

    # Run through a nested bash so the outer shell sees a normal exit
    # status instead of printing an "Aborted" notice for the expected
    # SIGABRT crash; the notice itself is discarded. The program's own
    # stdout + stderr still land in $ACTUAL.
    if bash -c '"$1" > "$2" 2>&1' runbin "$OUT_BIN" "$ACTUAL" 2>/dev/null; then
        echo "---- program output (expected a runtime failure, got exit 0): ----"
        cat "$ACTUAL"
        rm -f "$OUT_CPP" "$OUT_BIN" "$LOG" "$ACTUAL"
        fail "$SRC (expected a runtime failure, exit 0)"
        continue
    fi

    rm -f "$OUT_CPP" "$OUT_BIN" "$LOG" "$ACTUAL"
    pass "$SRC"
done

echo
echo "passed: $PASSED  failed: $FAILED"

if [[ "$FAILED" -ne 0 ]]; then
    exit 1
fi

echo "✅ All spec tests passed."
