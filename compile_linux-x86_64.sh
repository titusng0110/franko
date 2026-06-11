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
    echo "Usage: ./compile_linux_x86_64.sh <source.fr>" >&2
    exit 1
fi

SRC="$1"

if [[ ! -f "$SRC" ]]; then
    echo "Error: source file not found: $SRC" >&2
    exit 1
fi

# This script is intentionally for Linux x86_64 only.
OS_NAME="$(uname -s)"
ARCH_NAME="$(uname -m)"

if [[ "$OS_NAME" != "Linux" ]]; then
    echo "Error: compile_linux_x86_64.sh only supports Linux." >&2
    echo "Detected OS: $OS_NAME" >&2
    exit 1
fi

if [[ "$ARCH_NAME" != "x86_64" ]]; then
    echo "Error: compile_linux_x86_64.sh only supports x86_64." >&2
    echo "Detected architecture: $ARCH_NAME" >&2
    exit 1
fi

ANTLR_JAR="$ROOT/lib/antlr-4.13.2-complete.jar"

if [[ ! -f "$ANTLR_JAR" ]]; then
    echo "Error: ANTLR jar not found at $ANTLR_JAR" >&2
    exit 1
fi

ANTLR_CP=".:$ANTLR_JAR:${CLASSPATH:-}"

BUILD_DIR="$ROOT/build"
GEN_DIR="$BUILD_DIR/generated"
CLS_DIR="$BUILD_DIR/classes"

# Bundled jemalloc for Linux x86_64.
JEMALLOC_DIR="$ROOT/third_party/jemalloc/linux-x86_64"
JEMALLOC_INCLUDE="$JEMALLOC_DIR/include"
JEMALLOC_HEADER="$JEMALLOC_INCLUDE/jemalloc/jemalloc.h"
JEMALLOC_LIB="$JEMALLOC_DIR/lib/libjemalloc.a"

if [[ ! -f "$JEMALLOC_HEADER" ]]; then
    echo "Error: bundled jemalloc header not found:" >&2
    echo "  $JEMALLOC_HEADER" >&2
    echo >&2
    echo "Expected layout:" >&2
    echo "  third_party/jemalloc/linux-x86_64/include/jemalloc/jemalloc.h" >&2
    exit 1
fi

if [[ ! -f "$JEMALLOC_LIB" ]]; then
    echo "Error: bundled jemalloc static library not found:" >&2
    echo "  $JEMALLOC_LIB" >&2
    echo >&2
    echo "Expected layout:" >&2
    echo "  third_party/jemalloc/linux-x86_64/lib/libjemalloc.a" >&2
    exit 1
fi

# Ensure compiled Java compiler classes exist.
if [[ ! -d "$CLS_DIR" ]]; then
    echo "Build not found. Running toolchain..."
    run "$ROOT/update.sh"
fi

BASENAME="${SRC%.fr}"
CPP_OUT="${BASENAME}.cpp"
BIN_OUT="${BASENAME}.out"

echo "Franko target: linux-x86_64"
echo "Source: $SRC"
echo "Generated C++: $CPP_OUT"
echo "Binary output: $BIN_OUT"
echo

run java -cp "$ANTLR_CP:$CLS_DIR:$GEN_DIR" Main "$SRC" -o "$CPP_OUT"
echo

run g++ \
  -O3 \
  -std=c++14 \
  -Wall \
  -Wextra \
  -Wpedantic \
  -Wshadow \
  -I"$ROOT/include" \
  -I"$JEMALLOC_INCLUDE" \
  "$CPP_OUT" \
  "$JEMALLOC_LIB" \
  -pthread \
  -ldl \
  -static-libstdc++ \
  -static-libgcc \
  -s \
  -o "$BIN_OUT"

echo
echo "Successfully compiled to binary output: $BIN_OUT"
echo
echo "✅ Compilation finished with statically linked jemalloc."