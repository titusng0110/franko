#!/usr/bin/env python3
"""Extended red-black benchmark: portable vs -march=native, and a
same-data-structure C++ control.

Three groups:

  franko  Franko source (benchmark/redblack_franko.fr), built by
          compile.py in default / -O / -Omax modes.
  cpp     hand-written C++ std::set reference
          (benchmark/redblack_cpp.cpp).
  pool    hand-written C++ port of the Franko array-pool tree
          (benchmark/redblack_cpp_pool.cpp). Same data structure and
          same output as the Franko binary, so it isolates language /
          code generation from data-structure effects.

Every group is built in portable and `-march=native` variants. All
binaries are built first; then one untimed warm-up per binary; then
REPS measured rounds in randomized (seeded) order, so thermal drift
and position effects are spread across modes instead of accumulating
on whichever mode happens to run last. Timings are whole-process wall
clock (`time.perf_counter`).

Usage:
    uv run python benchmark/benchmark_matrix.py [reps]
    uv run python benchmark/benchmark_matrix.py 10 --cpu 2

With `--cpu N`, the process (and therefore every benchmark child)
is pinned to CPU N on Linux. `taskset -c N` works too.
"""

from __future__ import annotations

import argparse
import os
import random
import shutil
import statistics
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BENCH = ROOT / "benchmark"

FRANKO_SRC = BENCH / "redblack_franko.fr"
FRANKO_BIN = BENCH / ("redblack_franko.exe" if sys.platform == "win32"
                      else "redblack_franko.out")
CPP_SRC = BENCH / "redblack_cpp.cpp"
CPP_POOL_SRC = BENCH / "redblack_cpp_pool.cpp"

STATIC_STDLIBS = [] if sys.platform == "darwin" else [
    "-static-libstdc++", "-static-libgcc",
]


@dataclass(frozen=True)
class Mode:
    label: str
    group: str                 # "franko" | "cpp" | "pool"
    kind: str                  # "franko" | "cpp"
    source: Path | None = None
    franko_flag: str | None = None
    native: bool = False
    asan: bool = False
    opt: str = "3"


# Build order matters for the Franko group: compile.py always writes the
# same benchmark/redblack_franko.cpp and .out paths, so each Franko build
# must be copied out before the next one starts. The final Franko build is
# the portable default, which leaves the checked-in generated C++ in the
# same form the normal benchmark leaves it.
MODES = (
    Mode("default-native", "franko", "franko", native=True),
    Mode("-O-native", "franko", "franko", franko_flag="-O", native=True),
    Mode("-Omax-native", "franko", "franko", franko_flag="-Omax", native=True),
    Mode("-Omax", "franko", "franko", franko_flag="-Omax"),
    Mode("-O", "franko", "franko", franko_flag="-O"),
    Mode("default", "franko", "franko"),

    Mode("cpp-O1-asan-native", "cpp", "cpp", CPP_SRC,
         native=True, asan=True, opt="1"),
    Mode("cpp-O1-native", "cpp", "cpp", CPP_SRC, native=True, opt="1"),
    Mode("cpp-O3-native", "cpp", "cpp", CPP_SRC, native=True, opt="3"),
    Mode("cpp-O1-asan", "cpp", "cpp", CPP_SRC, asan=True, opt="1"),
    Mode("cpp-O1", "cpp", "cpp", CPP_SRC, opt="1"),
    Mode("cpp-O3", "cpp", "cpp", CPP_SRC, opt="3"),

    Mode("cpp-pool-O1-asan-native", "pool", "cpp", CPP_POOL_SRC,
         native=True, asan=True, opt="1"),
    Mode("cpp-pool-O1-native", "pool", "cpp", CPP_POOL_SRC,
         native=True, opt="1"),
    Mode("cpp-pool-O3-native", "pool", "cpp", CPP_POOL_SRC,
         native=True, opt="3"),
    Mode("cpp-pool-O1-asan", "pool", "cpp", CPP_POOL_SRC,
         asan=True, opt="1"),
    Mode("cpp-pool-O1", "pool", "cpp", CPP_POOL_SRC, opt="1"),
    Mode("cpp-pool-O3", "pool", "cpp", CPP_POOL_SRC, opt="3"),
)

REPORT_ORDER = {
    "franko": (
        "default", "default-native",
        "-O", "-O-native",
        "-Omax", "-Omax-native",
    ),
    "cpp": (
        "cpp-O1-asan", "cpp-O1-asan-native",
        "cpp-O1", "cpp-O1-native",
        "cpp-O3", "cpp-O3-native",
    ),
    "pool": (
        "cpp-pool-O1-asan", "cpp-pool-O1-asan-native",
        "cpp-pool-O1", "cpp-pool-O1-native",
        "cpp-pool-O3", "cpp-pool-O3-native",
    ),
}

GROUP_TITLE = {
    "franko": "Franko (array pool, generated C++)",
    "cpp": "C++ std::set reference",
    "pool": "C++ array-pool control (same data structure as Franko)",
}


def build_mode(mode: Mode, out_dir: Path) -> Path:
    """Build one MODE, copy the binary to OUT_DIR, return its path."""
    if mode.kind == "franko":
        cmd = [sys.executable, str(ROOT / "compile.py")]
        if mode.franko_flag:
            cmd.append(mode.franko_flag)
        if mode.native:
            cmd += ["--target", "native"]
        cmd.append(str(FRANKO_SRC))
        dest = out_dir / f"franko-{mode.label}.out"
        print(f"--- building [{mode.label}]: {' '.join(cmd)} ---", flush=True)
        proc = subprocess.run(cmd, cwd=ROOT, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, text=True)
        if proc.returncode != 0:
            raise RuntimeError(f"build failed: {' '.join(cmd)}\n"
                               f"{proc.stdout[-4000:]}")
        shutil.copy2(FRANKO_BIN, dest)
        return dest

    assert mode.source is not None
    flags = [f"-O{mode.opt}", "-std=c++14"]
    if mode.native:
        flags.append("-march=native")
    if mode.asan:
        flags += ["-fsanitize=address", "-g", "-fno-omit-frame-pointer"]
    flags += STATIC_STDLIBS
    dest = out_dir / f"{mode.label}.out"
    cmd = ["g++", *flags, str(mode.source), "-o", str(dest)]
    print(f"--- building [{mode.label}]: {' '.join(cmd)} ---", flush=True)
    proc = subprocess.run(cmd, cwd=ROOT, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"build failed: {' '.join(cmd)}\n"
                           f"{proc.stdout[-4000:]}")
    return dest


def run_timed(binary: Path) -> tuple[float, str]:
    start = time.perf_counter()
    proc = subprocess.run([str(binary)], cwd=ROOT, check=False,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    elapsed = time.perf_counter() - start
    out = proc.stdout.decode("utf-8", errors="replace")
    if proc.returncode != 0:
        raise RuntimeError(f"{binary} exited {proc.returncode}:\n{out[-2000:]}")
    return elapsed, out


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


def print_group_table(group: str, reps: int, times: dict[str, list[float]],
                      sizes: dict[str, int]) -> None:
    print()
    print(f"### {GROUP_TITLE[group]}")
    header = ("| test | " + " | ".join(f"r{i + 1}" for i in range(reps))
              + " | mean | bin (KB) |")
    print(header)
    print("| --- | " + " | ".join("---" for _ in range(reps))
          + " | --- | --- |")
    for label in REPORT_ORDER[group]:
        row = " | ".join(f"{t:.3f}" for t in times[label])
        print(f"| {label} | {row} | {statistics.fmean(times[label]):.3f} "
              f"| {sizes[label] / 1024:.0f} |")

    print()
    print("| test | mean | median | min | max | SD | CV |")
    print("| --- | --- | --- | --- | --- | --- | --- |")
    for label in REPORT_ORDER[group]:
        s = summarize(times[label])
        print(f"| {label} | {s['mean']:.3f} | {s['median']:.3f} "
              f"| {s['min']:.3f} | {s['max']:.3f} | {s['sd']:.3f} "
              f"| {s['cv']:.1f}% |")


def fmt_speedup(a: str, b: str, means: dict[str, float]) -> tuple[str, float]:
    """Return (faster_label, speedup_of_faster_over_slower)."""
    ma, mb = means[a], means[b]
    if ma <= mb:
        return a, mb / ma if ma else float("inf")
    return b, ma / mb if mb else float("inf")


def print_comparison_table(title: str,
                           pairs: list[tuple[str, str, str]],
                           means: dict[str, float]) -> None:
    print()
    print(f"### {title}")
    print("| comparison | faster | speedup | note |")
    print("| --- | --- | --- | --- |")
    for a, b, note in pairs:
        faster, speedup = fmt_speedup(a, b, means)
        print(f"| {a} vs {b} | {faster} | {speedup:.2f}x | {note} |")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reps", nargs="?", type=int, default=10,
                        help="measured repetitions per mode (default: 10)")
    parser.add_argument("--cpu", type=int, default=None,
                        help="pin the benchmark to one CPU (Linux)")
    parser.add_argument("--seed", type=int, default=12600,
                        help="seed for the per-round run-order shuffle")
    parser.add_argument("--no-shuffle", action="store_true",
                        help="run modes in a fixed order each round")
    args = parser.parse_args()
    if args.reps < 1:
        parser.error("reps must be at least 1")

    if args.cpu is not None:
        try:
            os.sched_setaffinity(0, {args.cpu})
            print(f"Pinned to CPU {args.cpu}.")
        except (AttributeError, OSError) as exc:
            print(f"warning: could not pin to CPU {args.cpu}: {exc}")

    labels = [m.label for m in MODES]
    run_order = list(labels)
    rng = random.Random(args.seed)

    with tempfile.TemporaryDirectory(prefix="franko-bench-") as tmp:
        out_dir = Path(tmp)
        binaries: dict[str, Path] = {}
        sizes: dict[str, int] = {}
        for mode in MODES:
            binary = build_mode(mode, out_dir)
            binaries[mode.label] = binary
            sizes[mode.label] = binary.stat().st_size

        # One untimed warm-up per binary: page cache, CPU frequency and
        # ASan runtime state are warm before the measured rounds.
        for label in labels:
            print(f"[{label}] warm-up", flush=True)
            run_timed(binaries[label])

        times: dict[str, list[float]] = {label: [] for label in labels}
        outputs: dict[str, str] = {}
        for rep in range(args.reps):
            if not args.no_shuffle:
                rng.shuffle(run_order)
            for label in run_order:
                elapsed, out = run_timed(binaries[label])
                times[label].append(elapsed)
                outputs[label] = out
                print(f"[{label}] rep {rep + 1}/{args.reps}: "
                      f"{elapsed:.3f}s", flush=True)

        print()
        print("program outputs (must agree within each group):")
        group_outputs: dict[str, set[str]] = {}
        for group in ("franko", "cpp", "pool"):
            outs = {outputs[label] for label in REPORT_ORDER[group]}
            group_outputs[group] = outs
            for out in sorted(outs):
                print(f"[{group}] {out!r}")
        franko_ok = len(group_outputs["franko"]) == 1
        cpp_ok = len(group_outputs["cpp"]) == 1
        pool_ok = len(group_outputs["pool"]) == 1
        pool_matches_franko = group_outputs["pool"] == group_outputs["franko"]
        print(f"franko outputs agree: {franko_ok}")
        print(f"cpp std::set outputs agree: {cpp_ok}")
        print(f"cpp array-pool outputs agree: {pool_ok}")
        print(f"cpp array-pool output matches franko: {pool_matches_franko}")
        if not (franko_ok and cpp_ok and pool_ok and pool_matches_franko):
            return 1

        for group in ("franko", "cpp", "pool"):
            print_group_table(group, args.reps, times, sizes)

        means = {label: statistics.fmean(times[label]) for label in labels}

        # ------------------------------------------------------------
        # Native effect, as a signed percentage. Negative = native faster.
        # ------------------------------------------------------------
        print()
        print("### -march=native effect")
        print("| base build | portable (s) | native (s) | native delta |")
        print("| --- | --- | --- | --- |")
        native_pairs = [
            ("default", "default-native"),
            ("-O", "-O-native"),
            ("-Omax", "-Omax-native"),
            ("cpp-O1-asan", "cpp-O1-asan-native"),
            ("cpp-O1", "cpp-O1-native"),
            ("cpp-O3", "cpp-O3-native"),
            ("cpp-pool-O1-asan", "cpp-pool-O1-asan-native"),
            ("cpp-pool-O1", "cpp-pool-O1-native"),
            ("cpp-pool-O3", "cpp-pool-O3-native"),
        ]
        for base, native in native_pairs:
            delta = 100.0 * (means[native] / means[base] - 1.0)
            print(f"| {base} | {means[base]:.3f} | {means[native]:.3f} "
                  f"| {delta:+.1f}% |")

        print_comparison_table("Franko build-mode effects", [
            ("default", "-O", "portable"),
            ("default-native", "-O-native", "native"),
            ("-O", "-Omax", "portable"),
            ("-O-native", "-Omax-native", "native"),
            ("default", "-Omax", "portable"),
            ("default-native", "-Omax-native", "native"),
        ], means)

        print_comparison_table("C++ std::set build-mode effects", [
            ("cpp-O1-asan", "cpp-O1", "portable"),
            ("cpp-O1-asan-native", "cpp-O1-native", "native"),
            ("cpp-O1", "cpp-O3", "portable"),
            ("cpp-O1-native", "cpp-O3-native", "native"),
        ], means)

        print_comparison_table("C++ array-pool control build-mode effects", [
            ("cpp-pool-O1-asan", "cpp-pool-O1", "portable"),
            ("cpp-pool-O1-asan-native", "cpp-pool-O1-native", "native"),
            ("cpp-pool-O1", "cpp-pool-O3", "portable"),
            ("cpp-pool-O1-native", "cpp-pool-O3-native", "native"),
        ], means)

        print_comparison_table("Franko vs C++ std::set", [
            ("default", "cpp-O1-asan",
             "matched -O1 + ASan + system allocator"),
            ("default-native", "cpp-O1-asan-native", "matched, native"),
            ("-O", "cpp-O1", "Franko -O3 + jemalloc vs C++ -O1"),
            ("-O-native", "cpp-O1-native", "same, native"),
            ("-O", "cpp-O3", "Franko release vs C++ max"),
            ("-O-native", "cpp-O3-native", "same, native"),
            ("-Omax", "cpp-O3", "max vs max"),
            ("-Omax-native", "cpp-O3-native", "max vs max, native"),
        ], means)

        print_comparison_table("Franko vs same-data-structure C++ control", [
            ("-Omax", "cpp-pool-O3", "same array-pool design, release"),
            ("-Omax-native", "cpp-pool-O3-native", "same, native"),
            ("-O", "cpp-pool-O3", "Franko checked vs C++ unchecked"),
            ("-O-native", "cpp-pool-O3-native", "same, native"),
            ("default", "cpp-pool-O1-asan", "same design, ASan"),
            ("default-native", "cpp-pool-O1-asan-native", "same, native, ASan"),
        ], means)

    return 0


if __name__ == "__main__":
    sys.exit(main())
