#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Franko import feature tests
# ============================================================
#
# Compiles each import test fixture and compares the program output
# with its main.expected file.
#
# Generated artifacts (main.cpp, main.out, main.actual) are removed
# after a passing case and kept for inspection after a failure.
#
# Usage:
#   ./tests/run_import_tests.sh
# ============================================================

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

run_case() {
    local name="$1"
    local dir="$ROOT/tests/$name"

    echo "== $name =="

    "$ROOT/compile_linux-x86_64.sh" "$dir/main.fr"

    "$dir/main.out" > "$dir/main.actual"

    if diff -u "$dir/main.expected" "$dir/main.actual"; then
        echo "PASS: $name"
        rm -f "$dir/main.cpp" "$dir/main.out" "$dir/main.actual"
    else
        echo "FAIL: $name"
        exit 1
    fi

    echo
}

run_case import
run_case import_diamond
run_case import_cycle

echo "✅ All import tests passed."
