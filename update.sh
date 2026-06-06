#!/usr/bin/env bash
set -euo pipefail

# Resolve project root (script location)
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ANTLR_JAR="$ROOT/lib/antlr-4.13.2-complete.jar"

if [[ ! -f "$ANTLR_JAR" ]]; then
    echo "Error: ANTLR jar not found at $ANTLR_JAR"
    exit 1
fi

ANTLR_CP=".:$ANTLR_JAR:${CLASSPATH:-}"
BUILD_DIR="$ROOT/build"
GEN_DIR="$BUILD_DIR/generated"
CLS_DIR="$BUILD_DIR/classes"

STEP_NO=0

step() {
    local cmd="$1"
    STEP_NO=$((STEP_NO + 1))

    echo
    echo "[Step $STEP_NO] $cmd"
    read -r -p "Press Enter to run, or Ctrl+C to abort... "

    eval "$cmd"

    echo "[Step $STEP_NO] Done."
}

mkdir -p "$GEN_DIR" "$CLS_DIR"

step "rm -rf \"$GEN_DIR\"/* \"$CLS_DIR\"/*"

step "java -Xmx500M -cp \"$ANTLR_CP\" org.antlr.v4.Tool -visitor -o \"$GEN_DIR\" Franko.g4"

step "javac -cp \"$ANTLR_CP:$GEN_DIR\" -d \"$CLS_DIR\" *.java \"$GEN_DIR\"/*.java"

echo
echo "✅ Compiler toolchain updated successfully."