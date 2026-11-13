#!/usr/bin/env python3
"""In-place quicksort benchmark: Franko vs C++ vs std::sort.

    uv run python benchmark/quicksort_benchmark.py
    uv run python benchmark/quicksort_benchmark.py --cpu 2

Modes: Franko -Omax, a hand-written C++ -O3 port of the same algorithm,
and C++ std::sort as the reference. All modes generate the same splitmix64
stream internally, sort it in place, verify it, and print a checksum.
"""

from __future__ import annotations

import argparse
import random
import statistics
import tempfile
from dataclasses import dataclass
from pathlib import Path

from _bench_common import (
    BENCH,
    build_cpp,
    build_franko,
    elapsed_seconds,
    pin_cpu,
    run_capture,
    strip_elapsed,
)

DATASETS = (
    ("0-9", "3"),
    ("0.25pct", "5"),
    ("0.5pct", "4"),
    ("1pct", "2"),
    ("10pct", "1"),
    ("random", "0"),
)

# (label, count, sorts per chunk, time the whole chunk)
SIZES = (
    ("10M", "10000000", 10, True),
    ("1M", "1000000", 20, True),
    ("99k", "99000", 600, True),
    ("10k", "10000", 6000, True),
    ("1k", "1000", 60000, True),
)


@dataclass(frozen=True)
class Mode:
    label: str
    source: Path
    kind: str


MODES = (
    Mode("franko-inplace", BENCH / "quicksort_inplace_franko.fr", "franko"),
    Mode("cpp-inplace", BENCH / "quicksort_inplace_cpp.cpp", "cpp"),
    Mode("cpp-std-sort", BENCH / "quicksort_std_cpp.cpp", "cpp"),
)


def build(mode: Mode, out_dir: Path) -> Path:
    if mode.kind == "franko":
        return build_franko(mode.source, mode.label, out_dir, mode="omax")
    return build_cpp(mode.source, mode.label, out_dir, optimize="3")


def fmt(value: float, places: int = 6, best: bool = False) -> str:
    text = f"{value:.{places}f}"
    return f"**{text}**" if best else text


def print_mean_table(results, labels) -> None:
    print("Mean seconds")
    print("| size | dataset | " + " | ".join(labels) + " |")
    print("| --- | --- | " + " | ".join("---" for _ in labels) + " |")
    for size, count, _reps, _chunk in SIZES:
        for dataset, _arg in DATASETS:
            means = {label: statistics.fmean(results[(size, dataset, label)])
                     for label in labels}
            best = min(means.values())
            row = [fmt(means[label], best=means[label] == best) for label in labels]
            print(f"| {size} | {dataset} | " + " | ".join(row) + " |")


def print_slowdown_table(results, labels) -> None:
    base_label = "cpp-std-sort"
    print("Slowdown vs cpp-std-sort")
    print("| size | dataset | franko-inplace | cpp-inplace |")
    print("| --- | --- | --- | --- |")
    for size, _count, _reps, _chunk in SIZES:
        for dataset, _arg in DATASETS:
            mean = {label: statistics.fmean(results[(size, dataset, label)])
                    for label in labels}
            base = mean[base_label]
            print(
                f"| {size} | {dataset} | "
                f"{mean['franko-inplace'] / base:.3f}x | "
                f"{mean['cpp-inplace'] / base:.3f}x |"
            )


def print_wins(results, labels) -> None:
    print("Wins by size")
    print("| size | Franko | C++ |")
    print("| --- | ---: | ---: |")
    for size, _count, _reps, _chunk in SIZES:
        wins = {label: 0 for label in labels}
        for dataset, _arg in DATASETS:
            mean = {label: statistics.fmean(results[(size, dataset, label)])
                    for label in labels}
            wins[min(mean, key=mean.get)] += 1
        print(f"| {size} | {wins['franko-inplace']}/6 | {wins['cpp-inplace']}/6 |")


def print_per_key_table(results, labels) -> None:
    print("Per-key nanoseconds")
    print("| size | dataset | " + " | ".join(labels) + " |")
    print("| --- | --- | " + " | ".join("---:" for _ in labels) + " |")
    counts = {size: int(count) for size, count, _reps, _chunk in SIZES}
    for size, _count, _reps, _chunk in SIZES:
        for dataset, _arg in DATASETS:
            values = {
                label: statistics.fmean(results[(size, dataset, label)])
                * 1e9 / counts[size]
                for label in labels
            }
            best = min(values.values())
            row = [fmt(values[label], places=2, best=values[label] == best)
                   for label in labels]
            print(f"| {size} | {dataset} | " + " | ".join(row) + " |")


def print_chunk_table(chunk_totals, labels) -> None:
    print("Chunk timings")
    print("| size | dataset | mode | chunk s | sorts | mean/sort |")
    print("| --- | --- | --- | ---: | ---: | ---: |")
    for size, _count, reps, chunk in SIZES:
        if not chunk:
            continue
        for dataset, _arg in DATASETS:
            for label in labels:
                total = chunk_totals[(size, dataset, label)]
                print(f"| {size} | {dataset} | {label} | "
                      f"{total:.6f} | {reps} | {total / reps:.6f} |")


def print_binary_table(sizes, labels) -> None:
    print("Binaries")
    print("| mode | bin (KB) |")
    print("| --- | ---: |")
    for label in labels:
        print(f"| {label} | {sizes[label] / 1024:.0f} |")


def print_checksums(outputs, labels) -> None:
    print("Checksums")
    print("| size | dataset | checksum |")
    print("| --- | --- | ---: |")
    for size, _count, _reps, _chunk in SIZES:
        for dataset, _arg in DATASETS:
            out = outputs[(size, dataset, labels[0])]
            checksum = strip_elapsed(out).strip().splitlines()[-1].split()[-1]
            print(f"| {size} | {dataset} | {checksum} |")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cpu", type=int, default=None,
                        help="pin the benchmark to one CPU (Linux)")
    parser.add_argument("--seed", type=int, default=12600,
                        help="seed for the per-round run-order shuffle")
    parser.add_argument("--no-shuffle", action="store_true",
                        help="run modes in a fixed order each round")
    parser.add_argument("--only-franko", action="store_true",
                        help="time only the Franko implementation")
    args = parser.parse_args()

    pin_cpu(args.cpu)
    modes = (MODES[0],) if args.only_franko else MODES
    labels = [mode.label for mode in modes]
    rng = random.Random(args.seed)

    with tempfile.TemporaryDirectory(prefix="franko-quicksort-") as tmp:
        out_dir = Path(tmp)
        binaries = {mode.label: build(mode, out_dir) for mode in modes}
        sizes = {label: path.stat().st_size for label, path in binaries.items()}

        for size, count, _reps, _chunk in SIZES:
            for dataset, dataset_arg in DATASETS:
                for label in labels:
                    print(f"[{size}][{dataset}][{label}] warm-up", flush=True)
                    run_capture(binaries[label], [dataset_arg, count, "1"])

        results: dict[tuple[str, str, str], list[float]] = {}
        chunk_totals: dict[tuple[str, str, str], float] = {}
        outputs: dict[tuple[str, str, str], str] = {}

        for size, count, reps, chunk in SIZES:
            for dataset, dataset_arg in DATASETS:
                order = list(labels)
                if not args.no_shuffle:
                    rng.shuffle(order)
                for label in order:
                    if chunk:
                        out = run_capture(
                            binaries[label], [dataset_arg, count, str(reps)]
                        )
                        elapsed = elapsed_seconds(out)
                        results[(size, dataset, label)] = [elapsed / reps]
                        chunk_totals[(size, dataset, label)] = elapsed
                        outputs[(size, dataset, label)] = out
                        print(f"[{size}][{dataset}][{label}] chunk "
                              f"{elapsed:.3f}s / {reps} = "
                              f"{elapsed / reps:.6f}s", flush=True)
                    else:
                        samples = []
                        for rep in range(reps):
                            out = run_capture(
                                binaries[label], [dataset_arg, count, "1"]
                            )
                            elapsed = elapsed_seconds(out)
                            samples.append(elapsed)
                            outputs[(size, dataset, label)] = out
                            print(f"[{size}][{dataset}][{label}] rep "
                                  f"{rep + 1}/{reps}: {elapsed:.3f}s", flush=True)
                        results[(size, dataset, label)] = samples

        print()
        for size, _count, _reps, _chunk in SIZES:
            for dataset, _arg in DATASETS:
                unique = {
                    strip_elapsed(outputs[(size, dataset, label)])
                    for label in labels
                }
                print(f"[{size}][{dataset}] outputs agree: {len(unique) == 1}")
                for out in sorted(unique):
                    print(f"  {out!r}")
                if len(unique) != 1:
                    return 1

        print()
        print_mean_table(results, labels)
        print()
        if not args.only_franko:
            print_slowdown_table(results, labels)
            print()
            print_wins(results, labels)
            print()
        print_per_key_table(results, labels)
        print()
        print_chunk_table(chunk_totals, labels)
        print()
        print_binary_table(sizes, labels)
        print()
        print_checksums(outputs, labels)
        print()
        print("Raw runs")
        for size, _count, reps, chunk in SIZES:
            for dataset, _arg in DATASETS:
                for label in labels:
                    key = (size, dataset, label)
                    if chunk:
                        total = chunk_totals[key]
                        print(f"{size}/{dataset}/{label}: chunk {total:.6f}s "
                              f"/ {reps} = {total / reps:.6f}s")
                    else:
                        print(f"{size}/{dataset}/{label}: "
                              + " ".join(f"{t:.6f}" for t in results[key]))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
