#!/usr/bin/env bash
set -Eeuo pipefail

trap 'echo "Error: command failed at line $LINENO: $BASH_COMMAND" >&2' ERR

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

run() {
    printf '%q ' "$@"
    printf '\n'
    "$@"
}

if [[ $# -lt 1 ]]; then
    echo "Usage: ./compile.sh <source.fr>" >&2
    exit 1
fi

SRC="$1"

if [[ ! -f "$SRC" ]]; then
    echo "Error: source file not found: $SRC" >&2
    exit 1
fi

ANTLR_JAR="$ROOT/lib/antlr-4.13.2-complete.jar"

if [[ ! -f "$ANTLR_JAR" ]]; then
    echo "Error: ANTLR jar not found at $ANTLR_JAR"
    exit 1
fi

ANTLR_CP=".:$ANTLR_JAR:${CLASSPATH:-}"

BUILD_DIR="$ROOT/build"
GEN_DIR="$BUILD_DIR/generated"
CLS_DIR="$BUILD_DIR/classes"

# Ensure compiled classes exist
if [[ ! -d "$CLS_DIR" ]]; then
    echo "Build not found. Running toolchain..."
    run "$ROOT/update.sh"
fi

BASENAME="${SRC%.fr}"
CPP_OUT="${BASENAME}.cpp"
BIN_OUT="${BASENAME}.out"

echo "Source: $SRC"
echo

run java -cp "$ANTLR_CP:$CLS_DIR:$GEN_DIR" Main "$SRC" -o "$CPP_OUT"
echo

run g++ -O3 -std=c++14 -Wall -Wextra -Wpedantic -Wshadow \
    -I"$ROOT/include" \
    "$CPP_OUT" -o "$BIN_OUT"
echo "Successfully compiled to binary output: $BIN_OUT"
echo

echo "✅ Compilation finished."