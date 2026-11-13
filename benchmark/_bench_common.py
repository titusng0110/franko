"""Shared build and timing helpers for the benchmark harnesses."""

from __future__ import annotations

import os
import statistics
import subprocess
import sys
from collections.abc import Sequence
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BENCH = ROOT / "benchmark"
EXE = ".exe" if sys.platform == "win32" else ".out"

sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT))

from franko_tools import run

from compile import (
    cxx_compile_flags,
    cxx_family,
    find_cxx,
    franko_compile,
    link_flags,
    native_compile,
)


def _log_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def build_franko(
    source: Path,
    label: str,
    out_dir: Path,
    *,
    mode: str,
    native: bool = False,
) -> Path:
    """Transpile and compile one Franko benchmark build."""
    work = out_dir / label
    work.mkdir()
    cpp = work / f"{label}.cpp"
    binary = work / f"{label}{EXE}"
    log = work / "build.log"

    if franko_compile(source, cpp, mode=mode, log=log) != 0:
        raise RuntimeError(f"Franko build failed for {label}:\n{_log_text(log)}")

    if native_compile(
        cpp,
        binary,
        mode=mode,
        target="native" if native else None,
        log=log,
        copy_asan_runtime=True,
    ) != 0:
        raise RuntimeError(f"C++ build failed for {label}:\n{_log_text(log)}")

    return binary


def build_cpp(
    source: Path,
    label: str,
    out_dir: Path,
    *,
    optimize: str = "3",
    sanitize: bool = False,
    native: bool = False,
) -> Path:
    """Compile one C++ control with the same compiler/flags as compile.py."""
    work = out_dir / label
    work.mkdir()
    binary = work / f"{label}{EXE}"
    log = work / "build.log"

    msvc = cxx_family() == "msvc"
    flags = cxx_compile_flags(
        optimize=optimize,
        sanitize=sanitize,
        target="native" if native else None,
    )

    if msvc:
        if optimize == "1":
            flags = ["/O1" if flag == "/O2" else flag for flag in flags]
        flags.append(f"/Fo{work}{os.sep}")
        if sanitize:
            flags.append(f"/Fd{work / 'compile.pdb'}")
        cmd = [find_cxx(), *flags, str(source), f"/Fe{binary}"]
        if sanitize:
            cmd += ["/link", f"/PDB:{work / 'link.pdb'}"]
    else:
        cmd = [
            find_cxx(),
            *flags,
            str(source),
            *link_flags(sanitize=sanitize),
            "-o",
            str(binary),
        ]

    if run(cmd, log_file=log, echo=False, check=False) != 0:
        raise RuntimeError(f"C++ build failed for {label}:\n{_log_text(log)}")

    return binary


def pin_cpu(cpu: int | None) -> None:
    if cpu is None:
        return
    try:
        os.sched_setaffinity(0, {cpu})
        print(f"Pinned to CPU {cpu}.")
    except (AttributeError, OSError) as exc:
        print(f"warning: could not pin to CPU {cpu}: {exc}")


def run_capture(binary: Path, args: Sequence[str] = ()) -> str:
    completed = subprocess.run(
        [str(binary), *(str(arg) for arg in args)],
        cwd=ROOT,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    output = completed.stdout.decode("utf-8", errors="replace")
    if completed.returncode != 0:
        raise RuntimeError(
            f"{binary} exited {completed.returncode}:\n{output[-2000:]}"
        )
    return output


def elapsed_seconds(output: str) -> float:
    for line in reversed(output.splitlines()):
        parts = line.split()
        if len(parts) == 2 and parts[0] == "elapsed_ns":
            return int(parts[1]) / 1_000_000_000.0
    raise RuntimeError(f"missing elapsed_ns line in program output:\n{output[-2000:]}")


def strip_elapsed(output: str) -> str:
    lines = [
        line for line in output.splitlines()
        if not line.startswith("elapsed_ns ")
    ]
    return "\n".join(lines) + ("\n" if output.endswith("\n") else "")


def summarize(times: list[float]) -> dict[str, float]:
    mean = statistics.fmean(times)
    sd = statistics.stdev(times) if len(times) > 1 else 0.0
    return {
        "mean": mean,
        "median": statistics.median(times),
        "min": min(times),
        "max": max(times),
        "sd": sd,
        "cv": 100.0 * sd / mean if mean else 0.0,
    }
