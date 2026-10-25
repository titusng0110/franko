#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================
# Franko library tests
# ============================================================
#
# Tests the importable Franko standard-library modules in
# library_tests/ (tests/ covers the language itself and never
# imports library modules).
#
# Layout:
#
#   library_tests/<module>/<suite>/success/<case>/main.fr
#     Must compile, run, and produce stdout matching main.expected.
#     Optional main.warnings: every non-empty line must appear as a
#     substring of the compiler output, pinning expected warnings.
#     Optional main.flags: extra compiler flags, one per line
#     (word-split like the spec suite).
#     Optional main.args: each line is one command-line argument
#     passed verbatim to the binary (empty lines pass empty-string
#     arguments; a trailing newline does not add an argument).
#     Optional main.stdin: bytes fed to the binary on stdin
#     (carriage returns are stripped, so CRLF checkouts behave
#     like LF ones).
#
#   library_tests/<module>/<suite>/failure/<case>/main.fr
#     Must be REJECTED by the Franko compiler (non-zero exit).
#     Optional main.errors: every non-empty line (lines starting
#     with '#' are comments) must appear as a substring of the
#     compiler output, pinning the expected diagnostics.
#     Optional main.flags: extra compiler flags, same format.
#
#   library_tests/<module>/<suite>/runtime/<case>/main.fr
#     Must compile, but the binary must EXIT NON-ZERO when run. Used
#     for documented runtime aborts such as FFI buffer-bound panics.
#     Runtime messages are intentionally not pinned: they may change
#     without breaking the suite. Optional main.flags, main.args,
#     and main.stdin as in the success category.
#
# Platform suites:
#
#   A suite directory named exactly `win32` runs on Windows only and
#   is skipped everywhere else (the os win32 File API bridge is a
#   compile-time #error off Windows). A suite directory named exactly
#   `posix` runs on non-Windows platforms only and is skipped on
#   Windows (the os posix bridge is a compile-time #error on Windows;
#   there is no _read/_write fallback). Every other suite runs on all
#   platforms.
#
# Success golden outputs must be deterministic. Never put values whose
# rendering varies across runs or platforms (such as live addresses)
# in a .expected file; cover those through the runtime category, which
# is judged by exit status only.
#
# Generated artifacts (main.cpp, main.bin, main.actual, compiler logs)
# are removed after a passing case and kept for inspection after a
# failure.
#
# Usage:
#   ./library_tests/run_library_tests.sh
# ============================================================

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# The Franko compiler reads ProgramTemplate.cpp relative to the cwd.
CP=".:lib/antlr-4.13.2-complete.jar:build/generated:build/classes"
JEM_INC="third_party/jemalloc/linux-x86_64/include"
JEM_LIB="third_party/jemalloc/linux-x86_64/lib/libjemalloc.a"

# The import search directory is passed explicitly so library modules
# (e.g. "franko/os") resolve the same way regardless of cwd.
IMPORT_DIR="$ROOT/import"

# Ensure compiled Java compiler classes exist.
if [[ ! -f "$ROOT/build/classes/Main.class" ]]; then
    echo "Error: compiler toolchain not built: $ROOT/build/classes/Main.class" >&2
    echo "Run ./update_linux-x86_64.sh first." >&2
    exit 1
fi

PASSED=0
FAILED=0
SKIPPED=0

fail() {
    echo "FAIL: $1"
    FAILED=$((FAILED+1))
}

pass() {
    echo "PASS: $1"
    PASSED=$((PASSED+1))
}

skip() {
    echo "SKIP: $1"
    SKIPPED=$((SKIPPED+1))
}

# True when the win32-only suites may run on this machine.
is_windows() {
    case "$(uname -s)" in
        MINGW*|MSYS*|CYGWIN*) return 0 ;;
        *) return 1 ;;
    esac
}

# True when a case directory belongs to a Windows-only suite.
is_win32_suite() {
    [[ "$1" == */win32/* ]]
}

# True when a case directory belongs to a POSIX-only suite (the os
# posix bridge is a compile-time #error on Windows).
is_posix_suite() {
    [[ "$1" == */posix/* ]]
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
# Each non-empty, non-comment line is split on whitespace and appended
# to the compiler invocation.
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
# Optional per-case CLI arguments.
#
# Each line of main.args is one command-line argument passed verbatim
# to the binary. Empty lines pass empty-string arguments; a trailing
# newline does not add an argument.
# ------------------------------------------------------------
read_run_args() {
    local ARGS_FILE="$1"
    RUN_ARGS=()

    [[ -f "$ARGS_FILE" ]] || return 0

    while IFS= read -r LINE || [[ -n "$LINE" ]]; do
        LINE="${LINE%$'\r'}"
        RUN_ARGS+=("$LINE")
    done < "$ARGS_FILE"
}

# ------------------------------------------------------------
# Failure cases: the compiler must reject the program.
# ------------------------------------------------------------
shopt -s nullglob
for SRC in library_tests/*/*/failure/*/main.fr; do
    DIR="$(dirname "$SRC")"
    OUT_CPP="$DIR/main.cpp"
    LOG="$DIR/main.compile.log"
    ERRS="$DIR/main.errors"
    FLAGS="$DIR/main.flags"

    echo "=== $SRC ==="

    if is_win32_suite "$SRC" && ! is_windows; then
        skip "$SRC (windows-only suite)"
        continue
    fi

    if is_posix_suite "$SRC" && is_windows; then
        skip "$SRC (posix suite does not run on Windows)"
        continue
    fi

    read_flags "$FLAGS"

    if java -cp "$CP" Main "$SRC" -o "$OUT_CPP" -p "$IMPORT_DIR" ${EXTRA_FLAGS[@]+"${EXTRA_FLAGS[@]}"} > "$LOG" 2>&1; then
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
for SRC in library_tests/*/*/success/*/main.fr; do
    DIR="$(dirname "$SRC")"
    OUT_CPP="$DIR/main.cpp"
    OUT_BIN="$DIR/main.bin"
    ACTUAL="$DIR/main.actual"
    LOG="$DIR/main.compile.log"
    EXPECTED="$DIR/main.expected"
    WARN="$DIR/main.warnings"
    FLAGS="$DIR/main.flags"
    STDIN_FILE="$DIR/main.stdin"

    echo "=== $SRC ==="

    if is_win32_suite "$SRC" && ! is_windows; then
        skip "$SRC (windows-only suite)"
        continue
    fi

    if is_posix_suite "$SRC" && is_windows; then
        skip "$SRC (posix suite does not run on Windows)"
        continue
    fi

    read_flags "$FLAGS"
    read_run_args "$DIR/main.args"

    if ! java -cp "$CP" Main "$SRC" -o "$OUT_CPP" -p "$IMPORT_DIR" ${EXTRA_FLAGS[@]+"${EXTRA_FLAGS[@]}"} > "$LOG" 2>&1; then
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

    # main.stdin feeds the binary when present. Carriage returns are
    # stripped so CRLF checkouts behave like LF ones. With `set -o
    # pipefail` the pipeline status is the binary's exit status.
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
for SRC in library_tests/*/*/runtime/*/main.fr; do
    DIR="$(dirname "$SRC")"
    OUT_CPP="$DIR/main.cpp"
    OUT_BIN="$DIR/main.bin"
    ACTUAL="$DIR/main.actual"
    LOG="$DIR/main.compile.log"
    FLAGS="$DIR/main.flags"

    echo "=== $SRC ==="

    if is_win32_suite "$SRC" && ! is_windows; then
        skip "$SRC (windows-only suite)"
        continue
    fi

    if is_posix_suite "$SRC" && is_windows; then
        skip "$SRC (posix suite does not run on Windows)"
        continue
    fi

    read_flags "$FLAGS"
    read_run_args "$DIR/main.args"

    if ! java -cp "$CP" Main "$SRC" -o "$OUT_CPP" -p "$IMPORT_DIR" ${EXTRA_FLAGS[@]+"${EXTRA_FLAGS[@]}"} > "$LOG" 2>&1; then
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

    # main.stdin feeds the binary when present (see the success loop).
    # Run through a nested bash so the outer shell sees a normal exit
    # status instead of printing an "Aborted" notice for the expected
    # SIGABRT crash; the notice itself is discarded. The program's own
    # stdout + stderr still land in $ACTUAL. "$3" is main.stdin or
    # /dev/null; "$@" from $4 on are the binary arguments.
    STDIN_SRC="$DIR/main.stdin"
    [[ -f "$STDIN_SRC" ]] || STDIN_SRC="/dev/null"

    if bash -c 'tr -d "\r" < "$3" | "$1" "${@:4}" > "$2" 2>&1' runbin \
            "$OUT_BIN" "$ACTUAL" "$STDIN_SRC" ${RUN_ARGS[@]+"${RUN_ARGS[@]}"} 2>/dev/null; then
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
echo "passed: $PASSED  failed: $FAILED  skipped: $SKIPPED"

if [[ "$FAILED" -ne 0 ]]; then
    exit 1
fi

echo "✅ All library tests passed."
