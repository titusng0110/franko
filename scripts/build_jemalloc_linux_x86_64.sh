#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="5.3.1"

SRC="$ROOT/third_party/src/jemalloc-$VERSION.tar.bz2"
BUILD="$ROOT/third_party/build/jemalloc-linux-x86_64"
PREFIX="$ROOT/third_party/jemalloc/linux-x86_64"

if [[ ! -f "$SRC" ]]; then
    echo "Error: jemalloc source archive not found:"
    echo "  $SRC"
    exit 1
fi

echo "Building jemalloc $VERSION for linux-x86_64"
echo "Source:  $SRC"
echo "Build:   $BUILD"
echo "Install: $PREFIX"
echo

rm -rf "$BUILD"
rm -rf "$PREFIX"

mkdir -p "$BUILD"
mkdir -p "$PREFIX"

tar -xjf "$SRC" -C "$BUILD" --strip-components=1

cd "$BUILD"

./configure \
    --prefix="$PREFIX" \
    --disable-shared \
    --enable-static \
    --with-jemalloc-prefix=je_

make -j"$(nproc)"
make install

echo
echo "Pruning unnecessary jemalloc install files..."

# Keep only:
#   include/jemalloc/jemalloc.h
#   lib/libjemalloc.a

if [[ ! -f "$PREFIX/include/jemalloc/jemalloc.h" ]]; then
    echo "Error: expected jemalloc header missing after install:"
    echo "  $PREFIX/include/jemalloc/jemalloc.h"
    exit 1
fi

if [[ ! -f "$PREFIX/lib/libjemalloc.a" ]]; then
    echo "Error: expected jemalloc static library missing after install:"
    echo "  $PREFIX/lib/libjemalloc.a"
    exit 1
fi

rm -rf "$PREFIX/bin"
rm -rf "$PREFIX/share"
rm -rf "$PREFIX/lib/pkgconfig"
rm -f  "$PREFIX/lib/libjemalloc_pic.a"
rm -f  "$PREFIX/lib/libjemalloc.so"
rm -f  "$PREFIX/lib/libjemalloc.so."*
rm -f  "$PREFIX/lib/libjemalloc.dylib"
rm -f  "$PREFIX/lib/libjemalloc."*.dylib

echo
echo "Final jemalloc bundle:"
find "$PREFIX" -type f | sort

echo
echo "✅ jemalloc linux-x86_64 static bundle ready:"
echo "  $PREFIX/include/jemalloc/jemalloc.h"
echo "  $PREFIX/lib/libjemalloc.a"