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

With --target the build target is chosen: --target native builds for
the host <os>-<arch> and adds -march=native to the C++ step, optimizing
for the host CPU (e.g. wider SIMD in the runtime intrinsics); the
binary then requires a matching CPU. An explicit <os>-<arch> spec (os
in {linux, windows, macos}, arch in {x86_64, arm64}) selects that
Franko target (os::IS_* constants follow it); when it differs from the
host, only the Franko -> C++ transpilation runs and the C++ compiler is
not invoked. Without --target the target is the host and the C++ step
uses the portable baseline ISA.

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
    normalize_target,
    relative,
    run,
)


def franko_compile(
    src: Path | str,
    out_cpp: Path | str,
    *,
    mode: str = "asan",
    target: str | None = None,
    extra_flags: Sequence[str] = (),
    log: Path | None = None,
    echo: bool = False,
    check: bool = False,
) -> int:
    """Run the Franko compiler (Franko -> C++14) for one build MODE.

    MODE is "asan" (no Franko flag, system-allocator runtime header),
    "opt" (-O, jemalloc header) or "omax" (-Omax, unchecked header).
    The compiler always runs with `-p IMPORT_DIR --target <target>`,
    where TARGET is a canonical `<os>-<arch>` spec (the os::IS_*
    constants follow it) and defaults to the host. The `-p` default is
    what the CLI and every runner already resolve to (the Java default
    is a relative "import" dir under the repo-root cwd).

    Returns the compiler exit code so test runners can attribute a
    per-case failure; with CHECK a non-zero exit raises ToolError.
    """
    java = find_tool("java", JAVA_HINT)
    return run(
        [java, "-cp", franko_classpath(), "Main",
         src, "-o", out_cpp, "-p", IMPORT_DIR, "--target", target or host_target(),
         *extra_flags, *java_mode_flags(mode)],
        log_file=log, echo=echo, check=check,
    )


def native_compile(
    out_cpp: Path | str,
    out_bin: Path | str,
    *,
    mode: str = "asan",
    warnings: bool = True,
    target: str | None = None,
    log: Path | None = None,
    echo: bool = False,
    check: bool = False,
) -> int:
    """Compile generated C++14 to a native executable for one build MODE.

    "opt"/"omax" build -O3 against the bundled jemalloc; "asan" builds
    -O1 + AddressSanitizer against the system allocator. WARNINGS
    toggles the -Wall -Wextra -Wpedantic -Wshadow set (off for
    runtime-abort test builds). TARGET is the compile.py --target value
    ("native" adds -march=native to the C++ compiler).

    Returns the driver exit code (CHECK raises instead).
    """
    cxx = find_cxx()
    optimized = mode in ("opt", "omax")
    include, jemalloc_lib = (jemalloc_paths() if optimized else (None, None))
    return run(
        [cxx, *cxx_compile_flags(warnings, optimize="3" if optimized else ASAN_OPTIMIZE,
                                  sanitize=not optimized, target=target),
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
    parser.add_argument(
        "--target",
        metavar="TARGET",
        help='build target: "native" (host <os>-<arch>, and the C++ '
             "compiler gets -march=native), or an explicit <os>-<arch> "
             "(os in {linux, windows, macos}, arch in {x86_64, arm64}; "
             "aliases darwin, x64, amd64, aarch64). Defaults to the host "
             "<os>-<arch>. A target different from the host runs the Franko "
             "transpilation only and skips the C++ compiler",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)

    host = host_target()
    if args.target is None:
        franko_target = host
        march_native = False
    elif args.target == "native":
        franko_target = host
        march_native = True
    else:
        franko_target = normalize_target(args.target)
        march_native = False
    cross_target = franko_target != host

    source = Path(args.source).expanduser()
    if not source.is_absolute():
        source = Path.cwd() / source
    source = source.resolve()

    if not source.is_file():
        raise ToolError(f"source file not found: {args.source}")

    # Tool checks happen before any build step so a missing JDK or compiler
    # reports the real problem instead of a half-built output (the build
    # stages below re-resolve the same tools). A cross target only
    # transpiles, so no C++ compiler is required for it.
    find_tool("java", JAVA_HINT)
    if not cross_target:
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
    include, jemalloc_lib = (jemalloc_paths() if optimized and not cross_target else (None, None))

    base = source.with_suffix("") if source.suffix == ".fr" else source
    cpp_out = Path(f"{base}.cpp")
    # .exe on Windows, .out everywhere else (the names the old
    # compile_windows-x64.ps1 / compile_linux-x86_64.sh produced).
    bin_out = Path(f"{base}{executable_suffix() or '.out'}")

    print(f"Franko target: {franko_target}")
    if march_native:
        print("C++ target: native (-march=native)")
    print(f"Source: {relative(source)}")
    print(f"Generated C++: {relative(cpp_out)}")
    if cross_target:
        print(f"Binary output: skipped (target {franko_target} differs from host {host})")
    else:
        print(f"Binary output: {relative(bin_out)}")
    if mode == "omax":
        print("Build mode: max-speed (-O3, jemalloc, no bounds/unsafe_cast checks)")
    elif mode == "opt":
        if defaulted_opt:
            print("Build mode: optimized (-O3, jemalloc; Windows default, "
                  "AddressSanitizer unsupported)")
        else:
            print("Build mode: optimized (-O3, jemalloc)")
    else:
        print("Build mode: debug + AddressSanitizer "
              "(-O1 -fsanitize=address, system allocator)")
    if optimized and not cross_target:
        print(f"jemalloc header: {relative(include / 'jemalloc' / 'jemalloc.h')}")
        print(f"jemalloc static library: {relative(jemalloc_lib)}")
    print()

    # ------------------------------------------------------------
    # Franko -> C++14 -> native executable, shared with the test runners.
    # ------------------------------------------------------------
    franko_compile(source, cpp_out, mode=mode, target=franko_target, echo=True, check=True)

    if cross_target:
        print()
        print(f"Target {franko_target} differs from host {host}: "
              "Franko -> C++ transpilation only, skipping the C++ build.")
        print()
        print(f"Successfully transpiled to C++ output: {relative(cpp_out)}")
        return 0

    print()

    native_compile(cpp_out, bin_out, mode=mode,
                   target=args.target, echo=True, check=True)

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
