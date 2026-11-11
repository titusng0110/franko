"""Shared toolchain and subprocess helpers for the Franko build scripts."""

from __future__ import annotations

import os
import platform
import shlex
import shutil
import subprocess
import sys
from pathlib import Path


def _force_utf8_stdio() -> None:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, OSError, ValueError):
            pass


_force_utf8_stdio()

ROOT = Path(__file__).resolve().parent.parent
ANTLR_JAR = ROOT / "lib" / "antlr-4.13.2-complete.jar"
BUILD_DIR = ROOT / "build"
GENERATED_DIR = BUILD_DIR / "generated"
CLASSES_DIR = BUILD_DIR / "classes"
MAIN_CLASS = CLASSES_DIR / "Main.class"
GRAMMAR_FILE = ROOT / "Franko.g4"

IS_WINDOWS = sys.platform in ("win32", "msys", "cygwin")
PATH_SEPARATOR = ";" if IS_WINDOWS else ":"

JAVA_HINT = "Install a JDK (Java 25) and make sure java/javac are on PATH."


class ToolError(Exception):
    """A fatal, user-facing error."""


def relative(path: Path | str) -> str:
    path = Path(path)
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


def host_os() -> str:
    if IS_WINDOWS:
        return "windows"
    if sys.platform == "darwin":
        return "macos"
    if sys.platform.startswith("linux"):
        return "linux"
    raise ToolError(f"unsupported host OS: {sys.platform}")


def host_arch() -> str:
    machine = platform.machine().lower()
    if machine in ("x86_64", "amd64"):
        return "x86_64"
    if machine in ("arm64", "aarch64"):
        return "arm64"
    raise ToolError(f"unsupported host architecture: {machine!r}")


def host_target() -> str:
    return f"{host_os()}-{host_arch()}"


_OS_ALIASES = {
    "linux": "linux",
    "windows": "windows",
    "macos": "macos",
    "darwin": "macos",
}
_ARCH_ALIASES = {
    "x86_64": "x86_64",
    "x64": "x86_64",
    "amd64": "x86_64",
    "arm64": "arm64",
    "aarch64": "arm64",
}


def normalize_target(spec: str) -> str:
    os_name, _, arch = spec.lower().partition("-")
    if os_name not in _OS_ALIASES or arch not in _ARCH_ALIASES:
        raise ToolError(
            f"invalid target {spec!r}: expected <os>-<arch> with "
            "os in {linux, windows, macos} and arch in {x86_64, arm64}"
        )
    return f"{_OS_ALIASES[os_name]}-{_ARCH_ALIASES[arch]}"


def executable_suffix() -> str:
    return ".exe" if IS_WINDOWS else ""


def find_tool(name: str, hint: str = "") -> Path:
    found = shutil.which(name)
    if not found:
        message = f"required tool not found on PATH: {name}"
        if hint:
            message += f"\n{hint}"
        raise ToolError(message)
    return Path(found)


def format_command(cmd: list) -> str:
    parts = [str(part) for part in cmd]
    return subprocess.list2cmdline(parts) if IS_WINDOWS else shlex.join(parts)


def run(
    cmd: list,
    *,
    cwd: Path = ROOT,
    log_file: Path | None = None,
    echo: bool = True,
    check: bool = True,
) -> int:
    """Run a command, optionally capturing its output to LOG_FILE."""
    parts = [str(part) for part in cmd]
    if echo:
        print(format_command(parts))

    sys.stdout.flush()
    sys.stderr.flush()

    if log_file is None:
        completed = subprocess.run(parts, cwd=cwd, check=False)
    else:
        with Path(log_file).open("wb") as handle:
            completed = subprocess.run(
                parts,
                cwd=cwd,
                stdout=handle,
                stderr=subprocess.STDOUT,
                check=False,
            )

    if check and completed.returncode:
        raise ToolError(
            f"command failed with exit code {completed.returncode}: "
            f"{format_command(parts)}"
        )
    return completed.returncode


def _classpath(*extra: Path) -> str:
    entries = [".", str(ANTLR_JAR)]
    if os.environ.get("CLASSPATH"):
        entries.append(os.environ["CLASSPATH"])
    entries.extend(str(path) for path in extra)
    return PATH_SEPARATOR.join(entries)


def franko_classpath() -> str:
    return _classpath(CLASSES_DIR, GENERATED_DIR)


def build_toolchain(java_release: str | None = None) -> None:
    """Generate the ANTLR parser and rebuild the Java compiler classes."""
    release = java_release or os.environ.get("FRANKO_JAVA_RELEASE", "25")
    if not ANTLR_JAR.is_file():
        raise ToolError(f"ANTLR jar not found at {ANTLR_JAR}")

    java = find_tool("java", JAVA_HINT)
    javac = find_tool("javac", JAVA_HINT)

    shutil.rmtree(GENERATED_DIR, ignore_errors=True)
    shutil.rmtree(CLASSES_DIR, ignore_errors=True)
    GENERATED_DIR.mkdir(parents=True)
    CLASSES_DIR.mkdir(parents=True)

    run([
        java, "-Xmx500M", "-cp", _classpath(),
        "org.antlr.v4.Tool", "-visitor", "-o", GENERATED_DIR, GRAMMAR_FILE.name,
    ])

    sources = sorted(ROOT.glob("*.java")) + sorted(GENERATED_DIR.glob("*.java"))
    if not sources:
        raise ToolError(f"no Java source files found in {ROOT}")

    run([
        javac, "--release", release, "-Xlint:all,-auxiliaryclass",
        "-cp", _classpath(GENERATED_DIR), "-d", CLASSES_DIR, *sources,
    ])

    if not MAIN_CLASS.is_file():
        raise ToolError(f"toolchain build did not produce {relative(MAIN_CLASS)}")


def require_toolchain() -> None:
    if not MAIN_CLASS.is_file():
        raise ToolError(
            f"compiler toolchain not built: {relative(MAIN_CLASS)}\n"
            "Run `uv run python update_toolchain.py` first."
        )


def ensure_toolchain() -> None:
    if MAIN_CLASS.is_file():
        return
    print(
        f"Build not found ({relative(MAIN_CLASS)} missing). "
        "Running toolchain update..."
    )
    print()
    build_toolchain()
    print()
