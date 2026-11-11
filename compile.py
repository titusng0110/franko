#!/usr/bin/env python3
"""Compile a Franko program into a native executable.

Replaces compile_linux-x86_64.sh and compile_windows-x64.ps1; the same
command works on Linux, macOS and Windows:

    uv run python compile.py programs/binary_codec.fr
    uv run python compile.py -O programs/binary_codec.fr
    uv run python compile.py -o build/app.cpp -p ./import programs/app.fr

-o is optional and defaults to `<source>.cpp`; -p is optional and
defaults to `./import`. --jemalloc is Linux-only and opts into the
bundled allocator. Without -O the default is an unoptimised debug build with
AddressSanitizer using the system allocator (malloc/free):
GCC/Clang build with -O1 -fsanitize=address, and MSVC cl.exe on
Windows builds with its default optimization level plus
/fsanitize=address. The Windows g++/clang++
fallback cannot use AddressSanitizer, so there the default is the
optimized build below. With -O it is an optimized build (-O3 on
GCC/Clang, /O2 on MSVC); with -Omax it is the same optimized build but
with bounds checking and unsafe_cast range checking stripped for speed
(out-of-range access is then undefined behavior). ASan builds always use
the system allocator; -O/-Omax use it too unless --jemalloc is passed on
Linux. All modes share
include/FrankoRuntime.hpp; the C++ step selects the mode with
FRANKO_USE_JEMALLOC and FRANKO_BOUNDS_UNCHECKED.

With --emit-json the Franko stage also writes per-pass AST/SAST snapshots
and a diagnostic JSON file next to the generated .cpp.

With --target the build target is chosen: --target native builds for
the host <os>-<arch> and adds -march=native to the C++ step when it is
GCC/Clang, optimizing for the host CPU (MSVC has no equivalent and
keeps its baseline ISA); the binary then requires a matching CPU. An explicit <os>-<arch> spec (os
in {linux, windows, macos}, arch in {x86_64, arm64}) selects that
Franko target (the built-in #IS_* target constants follow it);
when it differs from the host, only the Franko -> C++ transpilation
runs and the C++ compiler is not invoked. Without --target the target
is the host and the C++ step uses the portable baseline ISA. The Java
`Main` driver requires an explicit source path, -o, -p, and --target;
this script supplies all of them, so its CLI remains the single place
that owns the defaults.

It runs the Franko compiler (Franko -> C++14), then compiles and links
the generated C++ with the host C++ toolchain (cl.exe on Windows, where
it is available; g++/clang++ otherwise). Output is written next to the
source file:

    programs/binary_codec.cpp
    programs/binary_codec.out     (Linux/macOS)
    programs/binary_codec.exe     (Windows)

The two build stages are also importable so the test runners share them
instead of reimplementing them: franko_compile() (Franko -> C++14) and
native_compile() (C++14 -> executable), both parameterized by build
mode ("asan" / "opt" / "omax").

The Java toolchain is built automatically when it is missing.
"""

import argparse
import os
import shutil
import sys
import tempfile
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
    cxx_family,
    ensure_jemalloc_bundle,
    ensure_toolchain,
    executable_suffix,
    find_cxx,
    find_tool,
    franko_classpath,
    host_os,
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
    import_dir: Path | str = IMPORT_DIR,
    extra_flags: Sequence[str] = (),
    emit_json: bool = False,
    log: Path | None = None,
    echo: bool = False,
    check: bool = False,
) -> int:
    """Run the Franko compiler (Franko -> C++14) for one build MODE.

    MODE is "asan" (no Franko flag; checked, system allocator),
    "opt" (-O; checked) or "omax" (-Omax; unchecked). The matching
    C++ defines are added by native_compile(); jemalloc is opt-in via
    compile.py --jemalloc on Linux.
    The compiler always runs with `-p IMPORT_DIR --target <target>`,
    where TARGET is a canonical `<os>-<arch>` spec (the built-in
    #IS_* target constants follow it) and defaults to the host.
    `Main` requires the source path, -o, -p, and --target; this helper
    supplies all of them so test runners and the CLI own the defaults.

    Returns the compiler exit code so test runners can attribute a
    per-case failure; with CHECK a non-zero exit raises ToolError.
    """
    java = find_tool("java", JAVA_HINT)
    return run(
        [java, "-cp", franko_classpath(), "Main",
         src, "-o", out_cpp, "-p", import_dir, "--target", target or host_target(),
         *(["--emit-json"] if emit_json else []),
         *extra_flags, *java_mode_flags(mode)],
        log_file=log, echo=echo, check=check,
    )


def _copy_msvc_asan_runtime(cxx: Path, out_bin: Path, *, echo: bool) -> None:
    """Copy the MSVC ASan runtime and its CRT DLLs next to OUT_BIN.

    Microsoft no longer ships a static ASan runtime on Windows (the
    /MT runtime thunk still loads clang_rt.asan_dynamic-<arch>.dll), so
    the DLL has to travel with the executable. That DLL is itself built
    against the dynamic CRT, so it also needs vcruntime140.dll and
    vcruntime140_1.dll; the UCRT API sets are part of Windows 10/11.
    """
    arch = "x86_64" if host_target().endswith("x86_64") else "aarch64"
    for name in (
        f"clang_rt.asan_dynamic-{arch}.dll",
        "vcruntime140.dll",
        "vcruntime140_1.dll",
    ):
        source = cxx.parent / name
        if not source.is_file():
            continue
        destination = out_bin.parent / name
        shutil.copy2(source, destination)
        if echo:
            print(f"MSVC runtime: {relative(destination)}")


def native_compile(
    out_cpp: Path | str,
    out_bin: Path | str,
    *,
    mode: str = "asan",
    jemalloc: bool = False,
    copy_asan_runtime: bool = False,
    target: str | None = None,
    log: Path | None = None,
    echo: bool = False,
    check: bool = False,
) -> int:
    """Compile generated C++14 to a native executable for one build MODE.

    "opt"/"omax" optimize with the host compiler (-O3 on GCC/Clang,
    /O2 on MSVC). JEMALLOC is opt-in and Linux-only: it links the bundled
    jemalloc and defines FRANKO_USE_JEMALLOC=1. macOS and Windows always
    use the system allocator. "omax" also defines
    FRANKO_BOUNDS_UNCHECKED=1. "asan" builds a debug binary with
    AddressSanitizer (GCC/Clang -O1; MSVC keeps its default /O level),
    which is only available on Windows through MSVC. Warnings are always
    on (/W4, or -Wall -Wextra -Wpedantic -Wshadow). TARGET is the
    compile.py --target value ("native" adds -march=native for
    GCC/Clang; MSVC uses the portable baseline).

    MSVC object files (and ASan PDBs) are written to a throwaway
    directory so test suites only leave the .cpp and executable behind.
    Optimized MSVC builds pass neither /Zi nor /PDB. With
    COPY_ASAN_RUNTIME, an ASan MSVC build also copies
    clang_rt.asan_dynamic-<arch>.dll plus vcruntime140.dll and
    vcruntime140_1.dll next to the executable.

    Returns the driver exit code (CHECK raises instead).
    """
    cxx = find_cxx()
    msvc = cxx_family() == "msvc"
    optimized = mode in ("opt", "omax")

    if jemalloc:
        if host_os() != "linux":
            raise ToolError("--jemalloc is only supported on Linux hosts")
        if mode == "asan":
            raise ToolError(
                "--jemalloc requires -O or -Omax: AddressSanitizer builds "
                "must use the system allocator"
            )
    include = jemalloc_lib = None
    if jemalloc:
        ensure_jemalloc_bundle()
        include, jemalloc_lib = jemalloc_paths()

    mode_defines = []
    if jemalloc:
        mode_defines.append("-DFRANKO_USE_JEMALLOC=1")
    if mode == "omax":
        mode_defines.append("-DFRANKO_BOUNDS_UNCHECKED=1")
    if msvc:
        mode_defines = [flag.replace("-D", "/D", 1) for flag in mode_defines]

    obj_dir = Path(tempfile.mkdtemp(prefix="franko-msvc-")) if msvc else None
    try:
        compile_flags = [
            *mode_defines,
            *cxx_compile_flags(
                optimize="3" if optimized else ASAN_OPTIMIZE,
                sanitize=not optimized,
                target=target,
            ),
            f"/I{INCLUDE_DIR}" if msvc else f"-I{INCLUDE_DIR}",
        ]
        if include is not None:
            compile_flags.append(f"/I{include}" if msvc else f"-I{include}")
        if obj_dir is not None:
            # Keep cl.exe out of the repository. PDBs only exist for the
            # ASan build, which is the only MSVC mode that passes /Zi.
            compile_flags.append(f"/Fo{obj_dir}{os.sep}")
            if not optimized:
                compile_flags.append(f"/Fd{obj_dir / 'compile.pdb'}")

        link_flags_and_output = [*link_flags(sanitize=not optimized)]
        if jemalloc_lib is not None:
            link_flags_and_output.append(str(jemalloc_lib))
        if obj_dir is not None:
            link_flags_and_output.append(f"/Fe{out_bin}")
            if not optimized:
                link_flags_and_output += [
                    "/link",
                    f"/PDB:{obj_dir / 'link.pdb'}",
                ]
        else:
            link_flags_and_output += ["-o", str(out_bin)]

        code = run(
            [cxx, *compile_flags, str(out_cpp), *link_flags_and_output],
            log_file=log, echo=echo, check=check,
        )
        if code == 0 and copy_asan_runtime and msvc and not optimized:
            _copy_msvc_asan_runtime(cxx, Path(out_bin), echo=echo)
        return code
    finally:
        if obj_dir is not None:
            shutil.rmtree(obj_dir, ignore_errors=True)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="compile.py",
        description="Compile a Franko (.fr) program into a native executable.",
    )
    parser.add_argument("source", help="path to the .fr source file")
    parser.add_argument(
        "-o",
        metavar="OUTPUT",
        help="generated C++ output file (default: <source>.cpp)",
    )
    parser.add_argument(
        "-p",
        metavar="IMPORT_DIR",
        default="./import",
        help="import search directory (default: ./import)",
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "-O",
        dest="optimize",
        action="store_true",
        help="optimized build (-O3 with GCC/Clang, /O2 with MSVC); "
             "use --jemalloc on Linux to link the bundled allocator. "
             "Without -O the default is AddressSanitizer (except with the "
             "Windows g++/clang++ fallback, which does not support ASan "
             "and defaults to this optimized build)",
    )
    mode.add_argument(
        "-Omax",
        dest="omax",
        action="store_true",
        help="maximum-speed build like -O but with bounds and unsafe_cast "
             "checking stripped (out-of-range access is undefined behavior)",
    )
    parser.add_argument(
        "--jemalloc",
        action="store_true",
        help="Linux only, with -O or -Omax: link the bundled jemalloc instead "
             "of the system allocator (FRANKO_USE_JEMALLOC=1). ASan builds "
             "must use the system allocator",
    )
    parser.add_argument(
        "--target",
        metavar="TARGET",
        help='build target: "native" (host <os>-<arch>, and a GCC/Clang '
             "C++ compiler gets -march=native; MSVC has no equivalent), or "
             "an explicit <os>-<arch> "
             "(os in {linux, windows, macos}, arch in {x86_64, arm64}; "
             "aliases darwin, x64, amd64, aarch64). Defaults to the host "
             "<os>-<arch>. A target different from the host runs the Franko "
             "transpilation only and skips the C++ compiler",
    )
    parser.add_argument(
        "--emit-json",
        action="store_true",
        help="write per-pass AST/SAST and diagnostic JSON artifacts next to "
             "the generated .cpp",
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

    if args.jemalloc and host_os() != "linux":
        raise ToolError("--jemalloc is only supported on Linux hosts")

    import_dir = Path(args.p).expanduser()
    if not import_dir.is_absolute():
        import_dir = Path.cwd() / import_dir
    import_dir = import_dir.resolve()

    # Tool checks happen before any build step so a missing JDK or compiler
    # reports the real problem instead of a half-built output (the build
    # stages below re-resolve the same tools). A cross target only
    # transpiles, so no C++ compiler is required for it.
    find_tool("java", JAVA_HINT)
    if not cross_target:
        find_cxx()
    ensure_toolchain()

    # The default is ASan where it is supported. The Windows g++/clang++
    # fallback has no MinGW ASan runtime, so it keeps the historical
    # optimized default instead.
    msvc = IS_WINDOWS and not cross_target and cxx_family() == "msvc"
    defaulted_opt = IS_WINDOWS and not cross_target and not msvc
    if args.omax:
        mode = "omax"
    elif args.optimize or defaulted_opt:
        mode = "opt"
    else:
        mode = "asan"
    if args.jemalloc and mode == "asan":
        raise ToolError(
            "--jemalloc requires -O or -Omax: AddressSanitizer builds must "
            "use the system allocator"
        )
    use_jemalloc = args.jemalloc and not cross_target
    include = jemalloc_lib = None
    if use_jemalloc:
        ensure_jemalloc_bundle()
        include, jemalloc_lib = jemalloc_paths()

    base = source.with_suffix("") if source.suffix == ".fr" else source
    if args.o:
        cpp_out = Path(args.o).expanduser()
        if not cpp_out.is_absolute():
            cpp_out = Path.cwd() / cpp_out
        cpp_out = cpp_out.resolve()
    else:
        cpp_out = Path(f"{base}.cpp")
    # .exe on Windows, .out everywhere else. When -o is given, the binary
    # follows the output .cpp stem instead of the source stem.
    bin_out = cpp_out.with_suffix(executable_suffix() or ".out")

    print(f"Franko target: {franko_target}")
    if march_native:
        if msvc:
            print("C++ target: native (MSVC has no -march=native; portable baseline ISA)")
        else:
            print("C++ target: native (-march=native)")
    print(f"Source: {relative(source)}")
    print(f"Import directory: {relative(import_dir)}")
    print(f"Generated C++: {relative(cpp_out)}")
    if cross_target:
        print(f"Binary output: skipped (target {franko_target} differs from host {host})")
    else:
        print(f"Binary output: {relative(bin_out)}")
    allocator = "jemalloc" if use_jemalloc else "system allocator"
    if IS_WINDOWS:
        if mode == "omax":
            print("Build mode: max-speed (/O2, system allocator, "
                  "no bounds/unsafe_cast checks)")
        elif mode == "opt":
            if defaulted_opt:
                print("Build mode: optimized (/O2, system allocator; "
                      "AddressSanitizer unsupported by the g++/clang++ fallback)")
            else:
                print("Build mode: optimized (/O2, system allocator)")
        else:
            print("Build mode: debug + AddressSanitizer "
                  "(default /O + /fsanitize=address, system allocator)")
    elif mode == "omax":
        print(f"Build mode: max-speed (-O3, {allocator}, "
              "no bounds/unsafe_cast checks)")
    elif mode == "opt":
        print(f"Build mode: optimized (-O3, {allocator})")
    else:
        print("Build mode: debug + AddressSanitizer "
              f"(-O1 -fsanitize=address, {allocator})")
    if include is not None and jemalloc_lib is not None:
        print(f"jemalloc header: {relative(include / 'jemalloc' / 'jemalloc.h')}")
        print(f"jemalloc static library: {relative(jemalloc_lib)}")
    print()

    # ------------------------------------------------------------
    # Franko -> C++14 -> native executable, shared with the test runners.
    # ------------------------------------------------------------
    franko_compile(
        source,
        cpp_out,
        mode=mode,
        target=franko_target,
        import_dir=import_dir,
        emit_json=args.emit_json,
        echo=True,
        check=True,
    )

    if cross_target:
        print()
        print(f"Target {franko_target} differs from host {host}: "
              "Franko -> C++ transpilation only, skipping the C++ build.")
        print()
        print(f"Successfully transpiled to C++ output: {relative(cpp_out)}")
        return 0

    print()

    native_compile(cpp_out, bin_out, mode=mode, jemalloc=use_jemalloc,
                   copy_asan_runtime=True,
                   target=args.target, echo=True, check=True)

    print()
    print(f"Successfully compiled to binary output: {relative(bin_out)}")
    print()
    if use_jemalloc:
        print("✅ Compilation finished with statically linked jemalloc.")
    elif mode == "asan":
        print("✅ Compilation finished with system allocator + AddressSanitizer.")
    else:
        print("✅ Compilation finished with the system allocator.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ToolError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
