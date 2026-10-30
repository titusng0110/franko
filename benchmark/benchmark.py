#!/usr/bin/env python3
"""Benchmark Franko build modes vs C++ std::set on a red-black workload.

Franko (benchmark/redblack_franko.fr), 3 modes built with compile.py:
    default  (-O1 + ASan + system allocator)
    -O       (-O3 + jemalloc, checked runtime)
    -Omax    (-O3 + jemalloc, unchecked runtime)

C++ (benchmark/redblack_cpp.cpp), 3 modes built with g++ (static
libstdc++/libgcc, like the Franko builds; ASan links dynamic, since
-static-libasan does not exist on every toolchain):
    cpp-O1-asan (g++ -O1 -fsanitize=address -g -fno-omit-frame-pointer)
    cpp-O1      (g++ -O1)
    cpp-O3      (g++ -O3)

Each test runs REPS times, timed with time.perf_counter(); the headline
number is the average. stdout must agree within each group (franko modes
with each other, cpp modes with each other) as a correctness
cross-check. Prints a markdown table.

Usage:
    uv run python benchmark/benchmark.py [reps]
"""

import statistics
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BENCH = ROOT / "benchmark"

FRANKO_SRC = BENCH / "redblack_franko.fr"
FRANKO_BIN = BENCH / ("redblack_franko.exe" if sys.platform == "win32"
                      else "redblack_franko.out")
CPP_SRC = BENCH / "redblack_cpp.cpp"

FRANKO_MODES = ("-Omax", "-O", "default")  # fastest first

# (label, g++ flags): the plain -O1 build plus an ASan-instrumented -O1
# twin, so the Franko default (-O1 + ASan) has a direct apples-to-apples
# counterpart. The -O3 reference stays as the speed ceiling.
CPP_MODES = (
    ("cpp-O1-asan", ["-O1", "-fsanitize=address", "-g",
                     "-fno-omit-frame-pointer"]),
    ("cpp-O1", ["-O1"]),
    ("cpp-O3", ["-O3"]),
)

# Same static libstdc++/libgcc the Franko builds use (see
# franko_tools.link_flags): the C++ reference must not get its std libs
# shared while Franko bundles them. No such flags on macOS (Apple's
# clang has no libstdc++ to link).
STATIC_STDLIBS = [] if sys.platform == "darwin" else [
    "-static-libstdc++", "-static-libgcc",
]


def build_franko(mode: str) -> Path:
    cmd = [sys.executable, str(ROOT / "compile.py")]
    if mode != "default":
        cmd.append(mode)
    cmd.append(str(FRANKO_SRC))
    print(f"--- building franko [{mode}] ---", flush=True)
    subprocess.run(cmd, cwd=ROOT, check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
    return FRANKO_BIN


def build_cpp(label: str, flags: list[str]) -> Path:
    binary = BENCH / f"redblack_cpp_{label.removeprefix('cpp-')}.out"
    cmd = ["g++", *flags, "-std=c++14", *STATIC_STDLIBS, str(CPP_SRC),
           "-o", str(binary)]
    print(f"--- building {label}: {' '.join(cmd)} ---", flush=True)
    subprocess.run(cmd, cwd=ROOT, check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
    return binary


def run_timed(binary: Path) -> tuple[float, str]:
    start = time.perf_counter()
    proc = subprocess.run([str(binary)], cwd=ROOT, check=False,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    elapsed = time.perf_counter() - start
    out = proc.stdout.decode("utf-8", errors="replace")
    if proc.returncode != 0:
        raise RuntimeError(f"{binary} exited {proc.returncode}:\n{out[-2000:]}")
    return elapsed, out


def bench(label: str, binary: Path, reps: int) -> tuple[list[float], str]:
    times: list[float] = []
    out = ""
    for rep in range(reps):
        elapsed, out = run_timed(binary)
        times.append(elapsed)
        print(f"[{label}] rep {rep + 1}/{reps}: {elapsed:.3f}s", flush=True)
    return times, out


def main() -> int:
    reps = int(sys.argv[1]) if len(sys.argv) > 1 else 10
    results: dict[str, list[float]] = {}
    outputs: dict[str, str] = {}
    sizes: dict[str, int] = {}

    for mode in FRANKO_MODES:
        binary = build_franko(mode)
        sizes[mode] = binary.stat().st_size
        results[mode], outputs[mode] = bench(mode, FRANKO_BIN, reps)

    cpp_outputs: dict[str, str] = {}
    for label, flags in CPP_MODES:
        binary = build_cpp(label, flags)
        sizes[label] = binary.stat().st_size
        results[label], cpp_outputs[label] = bench(label, binary, reps)
    outputs.update(cpp_outputs)

    print()
    print("program output (must agree within each group):")
    for label in list(FRANKO_MODES) + [m[0] for m in CPP_MODES]:
        print(f"[{label}] {outputs[label]!r}")
    franko_ok = len({outputs[m] for m in FRANKO_MODES}) == 1
    cpp_ok = len({outputs[m[0]] for m in CPP_MODES}) == 1
    print(f"franko outputs agree: {franko_ok}")
    print(f"cpp outputs agree: {cpp_ok}")
    if not (franko_ok and cpp_ok):
        return 1

    order = ["default", "-O", "-Omax", "cpp-O1-asan", "cpp-O1", "cpp-O3"]
    print()
    print("| test | " + " | ".join(f"run{i + 1} (s)"
                                   for i in range(reps)) + " | avg (s) | bin (KB) |")
    print("| --- | " + " | ".join("---" for _ in range(reps)) + " | --- | --- |")
    for label in order:
        row = " | ".join(f"{t:.3f}" for t in results[label])
        print(f"| {label} | {row} | {statistics.fmean(results[label]):.3f} | {sizes[label] / 1024:.0f} |")

    avg = {label: statistics.fmean(results[label]) for label in order}
    print()
    print(f"-O vs default: {avg['default'] / avg['-O']:.2f}x faster")
    print(f"-Omax vs -O: {avg['-O'] / avg['-Omax']:.2f}x faster")
    print(f"-Omax vs default: {avg['default'] / avg['-Omax']:.2f}x faster")
    print(f"cpp-O3 vs cpp-O1: {avg['cpp-O1'] / avg['cpp-O3']:.2f}x faster")
    print(f"cpp-O1 vs cpp-O1-asan: {avg['cpp-O1-asan'] / avg['cpp-O1']:.2f}x faster")
    print(f"franko default vs cpp-O1-asan: {avg['cpp-O1-asan'] / avg['default']:.2f}x "
          f"({'faster' if avg['cpp-O1-asan'] > avg['default'] else 'slower'})")
    print(f"cpp-O3 vs franko -Omax: {avg['-Omax'] / avg['cpp-O3']:.2f}x "
          f"({'faster' if avg['-Omax'] > avg['cpp-O3'] else 'slower'})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
