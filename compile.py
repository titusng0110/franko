#!/usr/bin/env python3
"""Compile a Franko program into a native executable.

    uv run python compile.py programs/binary_codec.fr
    uv run python compile.py -O programs/binary_codec.fr
    uv run python compile.py -O --jemalloc programs/binary_codec.fr
    uv run python compile.py -o build/app.cpp -p ./import programs/app.fr

The default build is ASan with the system allocator. -O/-Omax build with
the host compiler (-O3 on GCC/Clang, /O2 on MSVC). --jemalloc is a
Linux-only opt-in for -O/-Omax. Windows uses MSVC cl.exe when available
and falls back to g++/clang++; the fallback has no ASan, so its default
is -O. An explicit --target different from the host transpiles only.
Generated C++ is compiled with exceptions and RTTI disabled, and binaries
are left unstripped so they can be inspected with nm/gdb.
"""

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from collections.abc import Sequence
from functools import cache
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "scripts"))

from franko_tools import (
    IS_WINDOWS,
    JAVA_HINT,
    ToolError,
    ensure_toolchain,
    executable_suffix,
    find_tool,
    franko_classpath,
    host_arch,
    host_os,
    host_target,
    normalize_target,
    relative,
    run,
)

IMPORT_DIR = ROOT / "import"
INCLUDE_DIR = ROOT / "include"
JEMALLOC_ROOT = ROOT / "third_party" / "jemalloc"
JEMALLOC_ARCHIVES = ROOT / "third_party" / "artifacts"
ASAN_OPTIMIZE = "1"
CXX_HINT = (
    "Install g++ or clang++ on PATH, or set CXX to choose a compiler "
    "explicitly."
)
WINDOWS_CXX_HINT = (
    "Install Visual Studio (or VS Build Tools) with the 'Desktop "
    "development with C++' workload, or put g++/clang++ on PATH."
)


def jemalloc_paths() -> tuple[Path, Path]:
    if host_os() != "linux":
        raise ToolError("jemalloc is only supported on Linux hosts")
    bundle = JEMALLOC_ROOT / host_target()
    include = bundle / "include"
    library = bundle / "lib" / "libjemalloc.a"
    if not (include / "jemalloc" / "jemalloc.h").is_file() or not library.is_file():
        raise ToolError(
            f"bundled jemalloc not found under {relative(bundle)}\n"
            "Run the jemalloc workflow and put the resulting zip in "
            "third_party/artifacts/."
        )
    return include, library


def ensure_jemalloc_bundle() -> None:
    if host_os() != "linux":
        return
    bundle = JEMALLOC_ROOT / host_target()
    header = bundle / "include" / "jemalloc" / "jemalloc.h"
    library = bundle / "lib" / "libjemalloc.a"
    if header.is_file() and library.is_file():
        return

    archive = JEMALLOC_ARCHIVES / f"jemalloc-{host_target()}.zip"
    if not archive.is_file():
        raise ToolError(f"prebuilt jemalloc bundle not found: {relative(archive)}")

    print(f"extracting {relative(archive)} ...")
    with zipfile.ZipFile(archive) as zf:
        zf.extractall(bundle)
    if not header.is_file() or not library.is_file():
        raise ToolError(f"jemalloc bundle has an unexpected layout: {relative(archive)}")


def _vswhere() -> Path:
    for variable in ("ProgramFiles(x86)", "ProgramFiles"):
        base = os.environ.get(variable)
        if base:
            candidate = (
                Path(base) / "Microsoft Visual Studio" / "Installer" / "vswhere.exe"
            )
            if candidate.is_file():
                return candidate
    found = shutil.which("vswhere")
    if found:
        return Path(found)
    raise ToolError("vswhere.exe not found: install Visual Studio with the C++ workload")


@cache
def _msvc_environment() -> None:
    """Load the host-architecture MSVC environment from vcvars*.bat."""
    components = {
        "x86_64": "Microsoft.VisualStudio.Component.VC.Tools.x86.x64",
        "arm64": "Microsoft.VisualStudio.Component.VC.Tools.ARM64",
    }
    vcvars_names = {"x86_64": "vcvars64.bat", "arm64": "vcvarsarm64.bat"}
    arch = host_arch()

    completed = subprocess.run(
        [
            str(_vswhere()), "-latest", "-prerelease", "-products", "*",
            "-requires", components[arch], "-property", "installationPath",
        ],
        capture_output=True,
        text=True,
        errors="replace",
        check=False,
    )
    install = completed.stdout.strip()
    if completed.returncode or not install:
        raise ToolError(
            f"Visual Studio with the C++ build tools was not found for "
            f"Windows {arch}.\nRequired vswhere component: {components[arch]}"
        )

    vcvars = Path(install) / "VC" / "Auxiliary" / "Build" / vcvars_names[arch]
    if not vcvars.is_file():
        raise ToolError(f"Visual Studio environment script not found: {vcvars}")

    env = os.environ.copy()
    env["VSCMD_SKIP_SENDTELEMETRY"] = "1"
    completed = subprocess.run(
        ["cmd.exe", "/d", "/c", "call", str(vcvars), ">nul", "&&", "set"],
        env=env,
        capture_output=True,
        text=True,
        errors="replace",
        check=False,
    )
    if completed.returncode:
        details = (completed.stderr or completed.stdout).strip()
        raise ToolError(
            f"failed to initialize the MSVC environment with {vcvars}\n{details}"
        )

    for line in completed.stdout.splitlines():
        name, separator, value = line.partition("=")
        if separator and name:
            os.environ[name] = value


def _find_msvc() -> Path | None:
    try:
        _msvc_environment()
    except ToolError:
        return None
    found = shutil.which("cl")
    return Path(found) if found else None


def _env_cxx() -> str | None:
    value = os.environ.get("CXX")
    if value is None:
        return None
    value = value.strip()
    return value or None


@cache
def find_cxx() -> Path:
    if not IS_WINDOWS:
        override = _env_cxx()
        if override:
            return find_tool(override, CXX_HINT)

        names = ("clang++", "g++") if host_os() == "macos" else ("g++", "clang++")

        for name in names:
            if found := shutil.which(name):
                return Path(found)

        raise ToolError(
            f"required tool not found on PATH: g++ or clang++\n{CXX_HINT}"
        )

    if cl := _find_msvc():
        return cl

    for name in ("g++", "clang++"):
        if found := shutil.which(name):
            return Path(found)

    raise ToolError(
        "required C++ compiler not found: cl.exe (Visual Studio) or "
        f"g++/clang++ on PATH\n{WINDOWS_CXX_HINT}"
    )


@cache
def cxx_family() -> str:
    return "msvc" if find_cxx().stem.lower() == "cl" else "gnu"


def default_build_mode() -> str:
    """ASan everywhere it exists; the Windows MinGW fallback uses -O."""
    return "opt" if IS_WINDOWS and cxx_family() == "gnu" else "asan"


def cxx_compile_flags(
    optimize: str = "3",
    sanitize: bool = False,
    target: str | None = None,
) -> list[str]:
    if cxx_family() == "msvc":
        flags = ["/nologo", "/std:c++14", "/utf-8"]
        if target == "native":
            pass
        elif target is not None:
            normalize_target(target)
        flags.append("/W4")
        flags += ["/Zi", "/fsanitize=address", "/MT"] if sanitize else ["/O2", "/MT"]
        return flags

    if sanitize and IS_WINDOWS:
        raise ToolError(
            "AddressSanitizer is not supported by the Windows g++/clang++ "
            "fallback: install MSVC cl.exe or use -O"
        )

    flags = [f"-O{optimize}", "-std=c++14"]
    if target == "native":
        flags.append("-march=native")
    elif target is not None:
        normalize_target(target)
    flags += ["-Wall", "-Wextra", "-Wpedantic", "-Wshadow"]
    if sanitize:
        flags += ["-fsanitize=address", "-g", "-fno-omit-frame-pointer"]
    return flags


def link_flags(sanitize: bool = False) -> list[str]:
    if cxx_family() == "msvc":
        return []

    system = host_os()
    flags: list[str] = []
    if sanitize:
        flags.append("-fsanitize=address")
        if system == "linux":
            flags.append("-static-libasan")
    if system in ("linux", "macos"):
        flags.append("-pthread")
    if system == "linux":
        flags.append("-ldl")
    if system != "macos":
        flags += ["-static-libstdc++", "-static-libgcc"]
    return flags


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
    """Run the Franko compiler (Franko -> C++14)."""
    java = find_tool("java", JAVA_HINT)
    return run(
        [
            java, "-cp", franko_classpath(), "Main",
            src, "-o", out_cpp, "-p", import_dir,
            "--target", target or host_target(),
            *(["--emit-json"] if emit_json else []),
            *extra_flags,
            *(["-O"] if mode == "opt" else ["-Omax"] if mode == "omax" else []),
        ],
        log_file=log,
        echo=echo,
        check=check,
    )


def _copy_msvc_asan_runtime(cxx: Path, out_bin: Path, *, echo: bool) -> None:
    arch = "x86_64" if host_target().endswith("x86_64") else "aarch64"
    for name in (
        f"clang_rt.asan_dynamic-{arch}.dll",
        "vcruntime140.dll",
        "vcruntime140_1.dll",
    ):
        source = cxx.parent / name
        if source.is_file():
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
    """Compile generated C++14 to a native executable with exceptions off."""
    cxx = find_cxx()
    msvc = cxx_family() == "msvc"
    optimized = mode in ("opt", "omax")

    include = jemalloc_lib = None
    if jemalloc:
        if host_os() != "linux":
            raise ToolError("--jemalloc is only supported on Linux hosts")
        if not optimized:
            raise ToolError("--jemalloc requires -O or -Omax")
        ensure_jemalloc_bundle()
        include, jemalloc_lib = jemalloc_paths()
        if echo:
            print(f"jemalloc header: {relative(include / 'jemalloc' / 'jemalloc.h')}")
            print(f"jemalloc static library: {relative(jemalloc_lib)}")

    defines = []
    if jemalloc:
        defines.append("-DFRANKO_USE_JEMALLOC=1")
    if mode == "omax":
        defines.append("-DFRANKO_BOUNDS_UNCHECKED=1")
    if msvc:
        defines = [flag.replace("-D", "/D", 1) for flag in defines]

    flags = cxx_compile_flags(
        optimize="3" if optimized else ASAN_OPTIMIZE,
        sanitize=not optimized,
        target=target,
    )
    if msvc:
        flags += ["/EHs-c-", "/GR-"]
    else:
        flags += ["-fno-exceptions", "-fno-rtti"]

    obj_dir = Path(tempfile.mkdtemp(prefix="franko-msvc-")) if msvc else None
    try:
        cmd = [
            cxx,
            *defines,
            *flags,
            f"/I{INCLUDE_DIR}" if msvc else f"-I{INCLUDE_DIR}",
        ]
        if include is not None:
            cmd.append(f"/I{include}" if msvc else f"-I{include}")
        if obj_dir is not None:
            cmd.append(f"/Fo{obj_dir}{os.sep}")
            if not optimized:
                cmd.append(f"/Fd{obj_dir / 'compile.pdb'}")

        cmd.append(str(out_cpp))
        if jemalloc_lib is not None:
            cmd.append(str(jemalloc_lib))
        cmd += link_flags(sanitize=not optimized)

        if msvc:
            cmd.append(f"/Fe{out_bin}")
            if not optimized:
                cmd += ["/link", f"/PDB:{obj_dir / 'link.pdb'}"]
        else:
            cmd += ["-o", str(out_bin)]

        code = run(cmd, log_file=log, echo=echo, check=check)
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
        "--optimize",
        dest="optimize",
        action="store_true",
        help="optimized build (-O3 on GCC/Clang, /O2 on MSVC)",
    )
    mode.add_argument(
        "-Omax",
        dest="omax",
        action="store_true",
        help="like -O with bounds and unsafe_cast checks stripped",
    )
    parser.add_argument(
        "--jemalloc",
        action="store_true",
        help="Linux only, with -O or -Omax: link the bundled jemalloc",
    )
    parser.add_argument(
        "--target",
        metavar="TARGET",
        help='build target: "native" or <os>-<arch> '
        "(linux/windows/macos, x86_64/arm64; aliases darwin, x64, "
        "amd64, aarch64). Defaults to the host",
    )
    parser.add_argument(
        "--emit-json",
        action="store_true",
        help="write per-pass AST/SAST and diagnostic JSON next to the .cpp",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)

    host = host_target()
    if args.target in (None, "native"):
        franko_target = host
        march_native = args.target == "native"
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

    import_dir = Path(args.p).expanduser()
    if not import_dir.is_absolute():
        import_dir = Path.cwd() / import_dir
    import_dir = import_dir.resolve()

    mode = (
        "omax" if args.omax
        else "opt" if args.optimize
        else "asan" if cross_target
        else default_build_mode()
    )

    if args.jemalloc:
        if host_os() != "linux":
            raise ToolError("--jemalloc is only supported on Linux hosts")
        if mode == "asan":
            raise ToolError("--jemalloc requires -O or -Omax")
    use_jemalloc = args.jemalloc and not cross_target

    find_tool("java", JAVA_HINT)
    if not cross_target:
        find_cxx()
    ensure_toolchain()

    base = source.with_suffix("") if source.suffix == ".fr" else source
    if args.o:
        cpp_out = Path(args.o).expanduser()
        if not cpp_out.is_absolute():
            cpp_out = Path.cwd() / cpp_out
        cpp_out = cpp_out.resolve()
    else:
        cpp_out = Path(f"{base}.cpp")
    bin_out = cpp_out.with_suffix(executable_suffix() or ".out")

    print(f"Franko target: {franko_target}")
    if march_native:
        if not cross_target and cxx_family() == "msvc":
            print("C++ target: native (MSVC keeps its baseline ISA)")
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
    if mode == "omax":
        print(f"Build mode: max-speed ({allocator}, bounds checks stripped)")
    elif mode == "opt":
        print(f"Build mode: optimized ({allocator})")
    else:
        print(f"Build mode: debug + AddressSanitizer ({allocator})")
    print()

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
        print(f"Successfully transpiled to C++ output: {relative(cpp_out)}")
        return 0

    print()
    native_compile(
        cpp_out,
        bin_out,
        mode=mode,
        jemalloc=use_jemalloc,
        copy_asan_runtime=True,
        target=args.target,
        echo=True,
        check=True,
    )
    print()
    print(f"Successfully compiled to binary output: {relative(bin_out)}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ToolError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
