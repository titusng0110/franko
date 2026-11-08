#!/usr/bin/env python3
"""Quicksort benchmark matrix: 10,000,000 int64 keys, four datasets.

Datasets (selected by the first command-line argument):

  0-9      values uniform in 0..9
  1pct     1,000,000 distinct values (1% of N)
  10pct    10,000,000 distinct values (10% of N)
  random   full 63-bit random values (effectively all distinct)

Modes:

  franko-index     Franko -Omax, indirect index sort
                   (benchmark/quicksort_index_franko.fr)
  franko-inplace   Franko -Omax, values swapped in place
                   (benchmark/quicksort_inplace_franko.fr)
  cpp-index        Hand-written C++ port of the same index sort
                   (benchmark/quicksort_index_cpp.cpp)
  cpp-inplace      Hand-written C++ port of the same algorithm, in place
                   (benchmark/quicksort_inplace_cpp.cpp)
  cpp-std-sort     Plain C++ std::sort reference
                   (benchmark/quicksort_std_cpp.cpp)

Every mode generates the same splitmix64 stream internally, selects the
dataset, sorts, and prints only `sorted ok` and a checksum. There is no
bulk input or output. The harness builds everything first, runs one
untimed warm-up per binary per dataset, then REPS measured rounds per
dataset in randomized (seeded) order, optionally pinned to one CPU.
Timings are whole-process wall clock.

Usage:
    uv run python benchmark/quicksort_benchmark.py
    uv run python benchmark/quicksort_benchmark.py 10 --cpu 2
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
    Mode("franko-index", "franko", BENCH / "quicksort_index_franko.fr"),
    Mode("franko-inplace", "franko", BENCH / "quicksort_inplace_franko.fr"),
    Mode("cpp-index", "cpp", BENCH / "quicksort_index_cpp.cpp"),
    Mode("cpp-inplace", "cpp", BENCH / "quicksort_inplace_cpp.cpp"),
    Mode("cpp-std-sort", "cpp", BENCH / "quicksort_std_cpp.cpp"),
)

REPORT_ORDER = (
    "franko-index",
    "franko-inplace",
    "cpp-index",
    "cpp-inplace",
    "cpp-std-sort",
)

DATASETS = (
    ("0-9", "3"),
    ("1pct", "2"),
    ("10pct", "1"),
    ("random", "0"),
)


def build_mode(mode: Mode, out_dir: Path) -> Path:
    """Build one mode, copy its binary to out_dir, and return the path."""
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


def run_timed(binary: Path, dataset_arg: str) -> tuple[float, str]:
    start = time.perf_counter()
    proc = subprocess.run([str(binary), dataset_arg], cwd=ROOT, check=False,
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


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reps", nargs="?", type=int, default=10,
                        help="measured repetitions per dataset and mode "
                             "(default: 10)")
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

    labels = [mode.label for mode in MODES]
    run_order = list(labels)
    rng = random.Random(args.seed)

    with tempfile.TemporaryDirectory(prefix="franko-quicksort-") as tmp:
        out_dir = Path(tmp)
        binaries = {}
        sizes = {}
        for mode in MODES:
            binary = build_mode(mode, out_dir)
            binaries[mode.label] = binary
            sizes[mode.label] = binary.stat().st_size

        # One untimed warm-up per binary per dataset.
        for dataset, dataset_arg in DATASETS:
            for label in labels:
                print(f"[{dataset}][{label}] warm-up", flush=True)
                run_timed(binaries[label], dataset_arg)

        times = {dataset: {label: [] for label in labels}
                 for dataset, _ in DATASETS}
        outputs = {}
        for rep in range(args.reps):
            for dataset, dataset_arg in DATASETS:
                if args.no_shuffle:
                    order = labels
                else:
                    rng.shuffle(run_order)
                    order = run_order
                for label in order:
                    elapsed, out = run_timed(binaries[label], dataset_arg)
                    times[dataset][label].append(elapsed)
                    outputs[(dataset, label)] = out
                    print(f"[{dataset}][{label}] rep {rep + 1}/{args.reps}: "
                          f"{elapsed:.3f}s", flush=True)

        print()
        for dataset, _ in DATASETS:
            unique_outputs = {outputs[(dataset, label)] for label in labels}
            print(f"{dataset}: outputs agree: {len(unique_outputs) == 1}")
            for out in sorted(unique_outputs):
                print(f"  {out!r}")
            if len(unique_outputs) != 1:
                return 1

        print()
        print("Mean seconds")
        print("| dataset | " + " | ".join(REPORT_ORDER) + " |")
        print("| --- | " + " | ".join("---" for _ in REPORT_ORDER) + " |")
        means = {}
        for dataset, _ in DATASETS:
            row = []
            for label in REPORT_ORDER:
                mean = statistics.fmean(times[dataset][label])
                means[(dataset, label)] = mean
                row.append(f"{mean:.3f}")
            print(f"| {dataset} | " + " | ".join(row) + " |")

        print()
        print("Detailed statistics")
        print("| dataset | mode | mean | median | min | max | SD | CV |")
        print("| --- | --- | --- | --- | --- | --- | --- | --- |")
        for dataset, _ in DATASETS:
            for label in REPORT_ORDER:
                s = summarize(times[dataset][label])
                print(f"| {dataset} | {label} | {s['mean']:.3f} "
                      f"| {s['median']:.3f} | {s['min']:.3f} "
                      f"| {s['max']:.3f} | {s['sd']:.3f} | {s['cv']:.1f}% |")

        print()
        print("Slowdown vs cpp-std-sort")
        print("| dataset | franko-index | franko-inplace "
              "| cpp-index | cpp-inplace |")
        print("| --- | --- | --- | --- | --- |")
        for dataset, _ in DATASETS:
            base = means[(dataset, "cpp-std-sort")]
            row = []
            for label in ("franko-index", "franko-inplace",
                          "cpp-index", "cpp-inplace"):
                row.append(f"{means[(dataset, label)] / base:.2f}x")
            print(f"| {dataset} | " + " | ".join(row) + " |")

        print()
        print("Binaries")
        print("| mode | bin (KB) |")
        print("| --- | --- |")
        for label in REPORT_ORDER:
            print(f"| {label} | {sizes[label] / 1024:.0f} |")

        print()
        print("Raw runs")
        for dataset, _ in DATASETS:
            for label in REPORT_ORDER:
                print(f"{dataset}/{label}: "
                      + " ".join(f"{t:.3f}" for t in times[dataset][label]))

    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as exc:
        print(f"error: {exc}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)
