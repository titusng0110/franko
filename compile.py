#!/usr/bin/env python3
"""Compile a Franko program into a native executable.

Replaces compile_linux-x86_64.sh and compile_windows-x64.ps1; the same
command works on Linux, macOS and Windows:

    uv run python compile.py programs/bfs.fr
    uv run python compile.py -O programs/bfs.fr

Without -O the default is an unoptimised debug build with
AddressSanitizer (-O1 -fsanitize=address) using the
system allocator (malloc/free via FrankoRuntimeWithAsan.hpp), except
on Windows where AddressSanitizer is unsupported and the default is
already the optimized build below; with -O
it is an optimized build (-O3) using the bundled jemalloc; with -Omax
it is the same optimized build but against FrankoRuntimeMax.hpp,
which strips bounds checking and unsafe_cast range checking for speed
(out-of-range access is then undefined behavior). The flag is passed
through to the Franko compiler, which selects the matching runtime
header.

It runs the Franko compiler (Franko -> C++14), then links the generated
C++ against the bundled jemalloc for the host platform. Output is written
next to the source file:

    programs/bfs.cpp
    programs/bfs.out     (Linux/macOS)
    programs/bfs.exe     (Windows)

The two build stages are also importable so the test runners share them
instead of reimplementing them: franko_compile() (Franko -> C++14) and
native_compile() (C++14 -> executable), both parameterized by build
mode ("asan" / "opt" / "omax").

The Java toolchain is built automatically when it is missing.
"""

import argparse
import sys
from collections.abc import Sequence
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "scripts"))

from franko_tools import (
    ASAN_OPTIMIZE,
    IMPORT_DIR,
    INCLUDE_DIR,
    IS_WINDOWS,
    JAVA_HINT,
    ToolError,
    cxx_compile_flags,
    ensure_toolchain,
    executable_suffix,
    find_cxx,
    find_tool,
    franko_classpath,
    host_target,
    java_mode_flags,
    jemalloc_paths,
    link_flags,
    relative,
    run,
)


def franko_compile(
    src: Path | str,
    out_cpp: Path | str,
    *,
    mode: str = "asan",
    extra_flags: Sequence[str] = (),
    log: Path | None = None,
    echo: bool = False,
    check: bool = False,
) -> int:
    """Run the Franko compiler (Franko -> C++14) for one build MODE.

    MODE is "asan" (no Franko flag, system-allocator runtime header),
    "opt" (-O, jemalloc header) or "omax" (-Omax, unchecked header).
    The compiler always runs with `-p IMPORT_DIR --target <host>`,
    which is what the CLI and every runner already resolve to (the
    Java defaults are a relative "import" dir under the repo-root cwd
    and host detection, respectively).

    Returns the compiler exit code so test runners can attribute a
    per-case failure; with CHECK a non-zero exit raises ToolError.
    """
    java = find_tool("java", JAVA_HINT)
    return run(
        [java, "-cp", franko_classpath(), "Main",
         src, "-o", out_cpp, "-p", IMPORT_DIR, "--target", host_target(),
         *extra_flags, *java_mode_flags(mode)],
        log_file=log, echo=echo, check=check,
    )


def native_compile(
    out_cpp: Path | str,
    out_bin: Path | str,
    *,
    mode: str = "asan",
    warnings: str = "basic",
    log: Path | None = None,
    echo: bool = False,
    check: bool = False,
) -> int:
    """Compile generated C++14 to a native executable for one build MODE.

    "opt"/"omax" build -O3 against the bundled jemalloc; "asan" builds
    -O1 + AddressSanitizer against the system allocator. WARNINGS is
    the cxx_compile_flags warning level ("full" for the test suites,
    "basic" for compile.py and the program smoke test, "none" for
    runtime-abort cases).

    Returns the driver exit code (CHECK raises instead).
    """
    cxx = find_cxx()
    optimized = mode in ("opt", "omax")
    include, jemalloc_lib = (jemalloc_paths() if optimized else (None, None))
    return run(
        [cxx, *cxx_compile_flags(warnings, optimize="3" if optimized else ASAN_OPTIMIZE,
                                  sanitize=not optimized),
         f"-I{INCLUDE_DIR}", *([f"-I{include}"] if optimized else []),
         out_cpp, *([jemalloc_lib] if optimized else []),
         *link_flags(sanitize=not optimized), "-o", out_bin],
        log_file=log, echo=echo, check=check,
    )


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="compile.py",
        description="Compile a Franko (.fr) program into a native executable.",
    )
    parser.add_argument("source", help="path to the .fr source file")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "-O", "--optimize",
        action="store_true",
        help="optimized build with -O3 (default without -O on Linux/macOS: "
             "-O1 with AddressSanitizer; on Windows the default is "
             "already optimized, so this is a no-op there)",
    )
    mode.add_argument(
        "-Omax",
        action="store_true",
        help="maximum-speed build like -O but with the checked runtime "
             "replaced by FrankoRuntimeMax.hpp (no bounds or unsafe_cast "
             "checking; out-of-range access is undefined behavior)",
    )
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
    # reports the real problem instead of a half-built output (the build
    # stages below re-resolve the same tools).
    find_tool("java", JAVA_HINT)
    find_cxx()
    ensure_toolchain()

    # Windows cannot use AddressSanitizer, so the default build there is
    # already -O (and still passes -O to the Franko compiler so it
    # selects the jemalloc runtime header).
    defaulted_opt = IS_WINDOWS and not (args.optimize or args.Omax)
    if args.Omax:
        mode = "omax"
    elif args.optimize or defaulted_opt:
        mode = "opt"
    else:
        mode = "asan"
    optimized = mode in ("opt", "omax")
    include, jemalloc_lib = (jemalloc_paths() if optimized else (None, None))

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
    if mode == "omax":
        print("Build mode: max-speed (-O3, jemalloc, no bounds/unsafe_cast checks)")
        print(f"jemalloc header: {relative(include / 'jemalloc' / 'jemalloc.h')}")
        print(f"jemalloc static library: {relative(jemalloc_lib)}")
    elif mode == "opt":
        if defaulted_opt:
            print("Build mode: optimized (-O3, jemalloc; Windows default, "
                  "AddressSanitizer unsupported)")
        else:
            print("Build mode: optimized (-O3, jemalloc)")
        print(f"jemalloc header: {relative(include / 'jemalloc' / 'jemalloc.h')}")
        print(f"jemalloc static library: {relative(jemalloc_lib)}")
    else:
        print("Build mode: debug + AddressSanitizer "
              "(-O1 -fsanitize=address, system allocator)")
    print()

    # ------------------------------------------------------------
    # Franko -> C++14 -> native executable, shared with the test runners.
    # ------------------------------------------------------------
    franko_compile(source, cpp_out, mode=mode, echo=True, check=True)
    print()

    native_compile(cpp_out, bin_out, mode=mode, warnings="basic",
                   echo=True, check=True)

    print()
    print(f"Successfully compiled to binary output: {relative(bin_out)}")
    print()
    if optimized:
        print("✅ Compilation finished with statically linked jemalloc.")
    else:
        print("✅ Compilation finished with system allocator + AddressSanitizer.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ToolError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
