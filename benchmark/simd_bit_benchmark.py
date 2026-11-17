#!/usr/bin/env python3
"""ARM64 SIMD and scalar bit intrinsics vs naive Franko.

    uv run python benchmark/simd_bit_benchmark.py
    uv run python benchmark/simd_bit_benchmark.py --runs 8

Builds both fixtures with -Omax, runs them several times, verifies that the
intrinsic and naive paths produce the same checksum, and prints median
nanoseconds per call. The ARM SIMD fixture is arm64-only.
"""

from __future__ import annotations

import argparse
import platform
import statistics
import sys
import tempfile
from pathlib import Path

from _bench_common import BENCH, build_franko, pin_cpu, run_capture

SIMD_SOURCE = BENCH / "simd_arm64_vs_naive.fr"
BIT_SOURCE = BENCH / "bit_vs_naive.fr"


def parse_output(output: str) -> tuple[dict[str, tuple[int, int, int]], tuple[int, int]]:
    rows: dict[str, tuple[int, int, int]] = {}
    checksums: tuple[int, int] | None = None

    for line in output.splitlines():
        parts = line.split()
        if not parts:
            continue
        if parts[0] == "simd_checksum" and len(parts) == 4:
            checksums = (int(parts[1]), int(parts[3]))
        elif len(parts) == 4:
            name, simd_ns, naive_ns, reps = parts
            rows[name] = (int(simd_ns), int(naive_ns), int(reps))

    if checksums is None:
        raise RuntimeError(f"missing checksum line in benchmark output:\n{output[-2000:]}")
    if not rows:
        raise RuntimeError(f"missing benchmark rows in output:\n{output[-2000:]}")

    return rows, checksums


def run_binary(binary: Path, runs: int) -> tuple[dict[str, tuple[float, float]], int]:
    samples: dict[str, list[tuple[int, int]]] = {}
    reps = 0

    for _ in range(runs):
        rows, checksums = parse_output(run_capture(binary))
        if checksums[0] != checksums[1]:
            raise RuntimeError(
                f"checksum mismatch for {binary.name}: "
                f"intrinsic={checksums[0]} naive={checksums[1]}"
            )
        for name, (simd_ns, naive_ns, row_reps) in rows.items():
            samples.setdefault(name, []).append((simd_ns, naive_ns))
            reps = row_reps

    medians = {
        name: (
            statistics.median(item[0] for item in values) / reps,
            statistics.median(item[1] for item in values) / reps,
        )
        for name, values in samples.items()
    }
    return medians, reps


def print_table(title: str, medians: dict[str, tuple[float, float]], reps: int) -> None:
    print()
    print(f"### {title}")
    print()
    print(f"Median of runs, {reps:,} calls per case.")
    print()
    print("| function | intrinsic ns/op | naive ns/op | speedup |")
    print("| --- | ---: | ---: | ---: |")
    for name, (intrinsic, naive) in medians.items():
        print(f"| `{name}` | {intrinsic:.3f} | {naive:.3f} | {naive / intrinsic:.2f}x |")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runs", type=int, default=5, help="timed runs per fixture")
    parser.add_argument("--cpu", type=int, default=None, help="pin to one CPU")
    args = parser.parse_args()

    if args.runs < 1:
        parser.error("--runs must be at least 1")

    machine = platform.machine().lower()
    if machine not in ("arm64", "aarch64"):
        print(
            "error: the SIMD fixture uses simd::arm and is arm64-only",
            file=sys.stderr,
        )
        return 1

    pin_cpu(args.cpu)
    print(f"host: {platform.platform()} ({machine})")
    print("build: Franko -Omax / C++ -O3, bounds checks stripped")

    with tempfile.TemporaryDirectory(prefix="franko-intrinsics-") as tmp:
        work = Path(tmp)
        simd_binary = build_franko(
            SIMD_SOURCE, "simd-arm64", work, mode="omax"
        )
        bit_binary = build_franko(BIT_SOURCE, "bit", work, mode="omax")

        simd_medians, simd_reps = run_binary(simd_binary, args.runs)
        bit_medians, bit_reps = run_binary(bit_binary, args.runs)

    print_table("ARM64 SIMD vs naive Franko", simd_medians, simd_reps)
    print_table("franko/bit vs naive Franko", bit_medians, bit_reps)
    print()
    print("Intrinsic and naive checksums matched for every run.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
