#!/usr/bin/env python3
"""Compile a Franko program into a native executable.

Replaces compile_linux-x86_64.sh and compile_windows-x64.ps1; the same
command works on Linux, macOS and Windows:

    uv run python compile.py programs/bfs.fr

It runs the Franko compiler (Franko -> C++14), then links the generated
C++ against the bundled jemalloc for the host platform. Output is written
next to the source file:

    programs/bfs.cpp
    programs/bfs.out     (Linux/macOS)
    programs/bfs.exe     (Windows)

The Java toolchain is built automatically when it is missing.
"""

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "scripts"))

from franko_tools import (
    IMPORT_DIR,
    INCLUDE_DIR,
    JAVA_HINT,
    ToolError,
    cxx_compile_flags,
    ensure_toolchain,
    executable_suffix,
    find_cxx,
    find_tool,
    franko_classpath,
    host_target,
    jemalloc_paths,
    link_flags,
    relative,
    run,
)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="compile.py",
        description="Compile a Franko (.fr) program into a native executable.",
    )
    parser.add_argument("source", help="path to the .fr source file")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)

    source = Path(args.source).expanduser()
    if not source.is_absolute():
        source = Path.cwd() / source
    source = source.resolve()

    if not source.is_file():
        raise ToolError(f"source file not found: {args.source}")

    # Tool checks happen before any build step so a missing JDK or compiler
    # reports the real problem instead of a half-built output.
    java = find_tool("java", JAVA_HINT)
    cxx = find_cxx()
    ensure_toolchain()

    include, jemalloc_lib = jemalloc_paths()

    target = host_target()

    base = source.with_suffix("") if source.suffix == ".fr" else source
    cpp_out = Path(f"{base}.cpp")
    # .exe on Windows, .out everywhere else (the names the old
    # compile_windows-x64.ps1 / compile_linux-x86_64.sh produced).
    bin_out = Path(f"{base}{executable_suffix() or '.out'}")

    print(f"Franko target: {target}")
    print(f"Source: {relative(source)}")
    print(f"Generated C++: {relative(cpp_out)}")
    print(f"Binary output: {relative(bin_out)}")
    print(f"jemalloc header: {relative(include / 'jemalloc' / 'jemalloc.h')}")
    print(f"jemalloc static library: {relative(jemalloc_lib)}")
    print()

    # ------------------------------------------------------------
    # Franko -> C++14
    # ------------------------------------------------------------
    run([
        java, "-cp", franko_classpath(), "Main",
        source, "-o", cpp_out, "-p", IMPORT_DIR, "--target", target,
    ])
    print()

    # ------------------------------------------------------------
    # C++14 -> native executable
    # ------------------------------------------------------------
    #
    # -I<include> lets FrankoRuntime.hpp include <jemalloc/jemalloc.h>;
    # the archive is passed by full path (do not switch to -L/-l unless it
    # is renamed to libjemalloc.a, because GCC's -l search convention may
    # not pick up jemalloc.lib).
    #
    compile_flags = cxx_compile_flags("basic")
    command = [
        cxx,
        *compile_flags,
        f"-I{INCLUDE_DIR}",
        f"-I{include}",
        cpp_out,
        jemalloc_lib,
        *link_flags(),
        "-o",
        bin_out,
    ]
    run(command)

    print()
    print(f"Successfully compiled to binary output: {relative(bin_out)}")
    print()
    print("✅ Compilation finished with statically linked jemalloc.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ToolError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
