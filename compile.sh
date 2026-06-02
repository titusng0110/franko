#!/usr/bin/env bash
set -Eeuo pipefail

trap 'echo "Error: command failed at line $LINENO: $BASH_COMMAND" >&2' ERR

if [[ $# -lt 1 ]]; then
    echo "Usage: ./compile.sh <source.fr>" >&2
    exit 1
fi

SRC="$1"

if [[ ! -f "$SRC" ]]; then
    echo "Error: source file not found: $SRC" >&2
    exit 1
fi

ANTLR_JAR="/usr/local/lib/antlr-4.13.2-complete.jar"
ANTLR_CP=".:$ANTLR_JAR:${CLASSPATH:-}"

BASENAME="${SRC%.fr}"
CPP_OUT="${BASENAME}.cpp"
BIN_OUT="${BASENAME}.out"

echo "Source:   $SRC"
echo "C++ out:  $CPP_OUT"
echo "Binary:   $BIN_OUT"

java -cp "$ANTLR_CP:build/classes:build/generated" Main "$SRC" -o "$CPP_OUT"

g++ -O3 -std=c++14 -Wall -Wextra -Wpedantic -Wshadow -Iinclude "$CPP_OUT" -o "$BIN_OUT"

echo
echo "Compilation finished."
echo "Generated:"
echo "  $CPP_OUT"
echo "  $BIN_OUT"