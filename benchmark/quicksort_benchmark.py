#!/usr/bin/env python3
"""In-place quicksort benchmark: Franko vs C++ vs std::sort.

Workload: plain int64 keys in four distributions and four sizes.

Datasets (first command-line argument to each binary):

  0-9      values uniform in 0..9
  1pct     values modulo 100,000
  10pct    values modulo 1,000,000
  random   full 63-bit random values

Sizes and repetitions:

  10M      10,000,000 keys   5 individual timed runs
  1M        1,000,000 keys  10 individual timed runs
  100k        100,000 keys  one chunk of 100 sorts
  10k          10,000 keys  one chunk of 1,000 sorts

The binaries accept three arguments: <dataset> <count> <repeat>. For the
100k and 10k rows the harness passes the full repetition count to a single
process and divides the chunk wall time by the repetition count, so process
startup is paid once per cell instead of once per sort.

Modes:

  franko-inplace   Franko -Omax, values swapped in place
  cpp-inplace      Hand-written C++ port, in place
  cpp-std-sort     Plain C++ std::sort reference

Every mode generates the same splitmix64 stream internally, sorts, and
prints only `sorted ok` and a checksum. There is no bulk input or output.
Timings are whole-process wall clock, including generation, sorting,
verification, and the checksum pass.

Usage:
    uv run python benchmark/quicksort_benchmark.py
    uv run python benchmark/quicksort_benchmark.py --cpu 2
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
EXE = ".exe" if sys.platform == "win32" else ".out"
STATIC_STDLIBS = [] if sys.platform == "darwin" else [
    "-static-libstdc++", "-static-libgcc",
]


@dataclass(frozen=True)
class Mode:
    label: str
    kind: str                  # "franko" | "cpp"
    source: Path


MODES = (
    Mode("franko-inplace", "franko", BENCH / "quicksort_inplace_franko.fr"),
    Mode("cpp-inplace", "cpp", BENCH / "quicksort_inplace_cpp.cpp"),
    Mode("cpp-std-sort", "cpp", BENCH / "quicksort_std_cpp.cpp"),
)

DATASETS = (
    ("0-9", "3"),
    ("1pct", "2"),
    ("10pct", "1"),
    ("random", "0"),
)

# (label, count argument, repetitions, time-as-one-chunk)
SIZES = (
    ("10M", "10000000", 5, False),
    ("1M", "1000000", 10, False),
    ("100k", "100000", 100, True),
    ("10k", "10000", 1000, True),
)


def build_mode(mode: Mode, out_dir: Path) -> Path:
    dest = out_dir / f"{mode.label}{EXE}"

    if mode.kind == "franko":
        cmd = [sys.executable, str(ROOT / "compile.py"), "-Omax",
               str(mode.source)]
        print(f"--- building [{mode.label}]: {' '.join(cmd)} ---", flush=True)
        proc = subprocess.run(cmd, cwd=ROOT, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, text=True)
        if proc.returncode != 0:
            raise RuntimeError(f"build failed: {' '.join(cmd)}\n"
                               f"{proc.stdout[-4000:]}")
        shutil.copy2(mode.source.with_suffix(EXE), dest)
        return dest

    cmd = ["g++", "-O3", "-std=c++17", *STATIC_STDLIBS,
           str(mode.source), "-o", str(dest)]
    print(f"--- building [{mode.label}]: {' '.join(cmd)} ---", flush=True)
    proc = subprocess.run(cmd, cwd=ROOT, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"build failed: {' '.join(cmd)}\n"
                           f"{proc.stdout[-4000:]}")
    return dest


def run_case(binary: Path, dataset_arg: str, count_arg: str,
             repeat_arg: str) -> tuple[float, str]:
    start = time.perf_counter()
    proc = subprocess.run([str(binary), dataset_arg, count_arg, repeat_arg],
                          cwd=ROOT, check=False, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT)
    elapsed = time.perf_counter() - start
    out = proc.stdout.decode("utf-8", errors="replace")
    if proc.returncode != 0:
        raise RuntimeError(f"{binary} exited {proc.returncode}:\n"
                           f"{out[-2000:]}")
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


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cpu", type=int, default=None,
                        help="pin the benchmark to one CPU (Linux)")
    parser.add_argument("--seed", type=int, default=12600,
                        help="seed for the per-round run-order shuffle")
    parser.add_argument("--no-shuffle", action="store_true",
                        help="run modes in a fixed order each round")
    args = parser.parse_args()

    if args.cpu is not None:
        try:
            os.sched_setaffinity(0, {args.cpu})
            print(f"Pinned to CPU {args.cpu}.")
        except (AttributeError, OSError) as exc:
            print(f"warning: could not pin to CPU {args.cpu}: {exc}")

    labels = [mode.label for mode in MODES]
    rng = random.Random(args.seed)

    with tempfile.TemporaryDirectory(prefix="franko-quicksort-") as tmp:
        out_dir = Path(tmp)
        binaries = {}
        sizes = {}
        for mode in MODES:
            binary = build_mode(mode, out_dir)
            binaries[mode.label] = binary
            sizes[mode.label] = binary.stat().st_size

        # One untimed warm-up per binary, size, and dataset.
        for size_label, count_arg, _reps, _chunk in SIZES:
            for dataset, dataset_arg in DATASETS:
                for label in labels:
                    print(f"[{size_label}][{dataset}][{label}] warm-up",
                          flush=True)
                    run_case(binaries[label], dataset_arg, count_arg, "1")

        results = {}
        outputs = {}
        chunk_totals = {}

        for size_label, count_arg, reps, chunk in SIZES:
            for dataset, dataset_arg in DATASETS:
                order = list(labels)
                if not args.no_shuffle:
                    rng.shuffle(order)
                for label in order:
                    if chunk:
                        elapsed, out = run_case(
                            binaries[label], dataset_arg, count_arg, str(reps))
                        results[(size_label, dataset, label)] = [elapsed / reps]
                        chunk_totals[(size_label, dataset, label)] = elapsed
                        outputs[(size_label, dataset, label)] = out
                        print(f"[{size_label}][{dataset}][{label}] "
                              f"chunk {elapsed:.3f}s / {reps} = "
                              f"{elapsed / reps:.6f}s", flush=True)
                    else:
                        samples = []
                        for rep in range(reps):
                            elapsed, out = run_case(
                                binaries[label], dataset_arg, count_arg, "1")
                            samples.append(elapsed)
                            outputs[(size_label, dataset, label)] = out
                            print(f"[{size_label}][{dataset}][{label}] "
                                  f"rep {rep + 1}/{reps}: {elapsed:.3f}s",
                                  flush=True)
                        results[(size_label, dataset, label)] = samples

        print()
        for size_label, _count, _reps, _chunk in SIZES:
            for dataset, _dataset_arg in DATASETS:
                unique = {outputs[(size_label, dataset, label)]
                          for label in labels}
                print(f"[{size_label}][{dataset}] outputs agree: "
                      f"{len(unique) == 1}")
                for out in sorted(unique):
                    print(f"  {out!r}")
                if len(unique) != 1:
                    return 1

        print()
        print("Mean seconds")
        print("| size | dataset | " + " | ".join(labels) + " |")
        print("| --- | --- | " + " | ".join("---" for _ in labels) + " |")
        means = {}
        for size_label, _count, _reps, _chunk in SIZES:
            for dataset, _dataset_arg in DATASETS:
                row = []
                for label in labels:
                    mean = statistics.fmean(
                        results[(size_label, dataset, label)])
                    means[(size_label, dataset, label)] = mean
                    row.append(f"{mean:.6f}")
                print(f"| {size_label} | {dataset} | "
                      + " | ".join(row) + " |")

        print()
        print("Slowdown vs cpp-std-sort")
        print("| size | dataset | franko-inplace | cpp-inplace |")
        print("| --- | --- | --- | --- |")
        for size_label, _count, _reps, _chunk in SIZES:
            for dataset, _dataset_arg in DATASETS:
                base = means[(size_label, dataset, "cpp-std-sort")]
                row = [f"{means[(size_label, dataset, label)] / base:.3f}x"
                       for label in ("franko-inplace", "cpp-inplace")]
                print(f"| {size_label} | {dataset} | "
                      + " | ".join(row) + " |")

        print()
        print("Detailed statistics (individual runs)")
        print("| size | dataset | mode | mean | median | min | max | SD | CV |")
        print("| --- | --- | --- | --- | --- | --- | --- | --- | --- |")
        for size_label, _count, reps, chunk in SIZES:
            if chunk:
                continue
            for dataset, _dataset_arg in DATASETS:
                for label in labels:
                    s = summarize(results[(size_label, dataset, label)])
                    print(f"| {size_label} | {dataset} | {label} | "
                          f"{s['mean']:.6f} | {s['median']:.6f} | "
                          f"{s['min']:.6f} | {s['max']:.6f} | "
                          f"{s['sd']:.6f} | {s['cv']:.1f}% |")

        print()
        print("Chunk timings (100k and 10k)")
        print("| size | dataset | mode | chunk s | reps | mean/sort |")
        print("| --- | --- | --- | --- | --- | --- |")
        for size_label, _count, reps, chunk in SIZES:
            if not chunk:
                continue
            for dataset, _dataset_arg in DATASETS:
                for label in labels:
                    total = chunk_totals[(size_label, dataset, label)]
                    print(f"| {size_label} | {dataset} | {label} | "
                          f"{total:.6f} | {reps} | {total / reps:.6f} |")

        print()
        print("Binaries")
        print("| mode | bin (KB) |")
        print("| --- | --- |")
        for label in labels:
            print(f"| {label} | {sizes[label] / 1024:.0f} |")

        print()
        print("Raw runs")
        for size_label, _count, reps, chunk in SIZES:
            for dataset, _dataset_arg in DATASETS:
                for label in labels:
                    key = (size_label, dataset, label)
                    if chunk:
                        total = chunk_totals[key]
                        print(f"{size_label}/{dataset}/{label}: "
                              f"chunk {total:.6f}s / {reps} = "
                              f"{total / reps:.6f}s")
                    else:
                        print(f"{size_label}/{dataset}/{label}: "
                              + " ".join(f"{t:.6f}"
                                         for t in results[key]))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
