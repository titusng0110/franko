#!/usr/bin/env bash
set -euo pipefail

ANTLR_JAR="/usr/local/lib/antlr-4.13.2-complete.jar"
ANTLR_CP=".:$ANTLR_JAR:${CLASSPATH:-}"
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

mkdir -p build/generated build/classes

step 'rm -f build/generated/* build/classes/*'

step 'java -Xmx500M -cp "'"$ANTLR_CP"'" org.antlr.v4.Tool -visitor -o build/generated Franko.g4'

step 'javac -cp "'"$ANTLR_CP:build/generated"'" -d build/classes *.java build/generated/*.java'

echo
echo "Compiler toolchain updated successfully."
