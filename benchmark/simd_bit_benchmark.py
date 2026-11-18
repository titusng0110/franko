#!/usr/bin/env python3
"""x86_64/ARM64 SIMD and scalar bit intrinsics vs naive Franko.

    uv run python benchmark/simd_bit_benchmark.py
    uv run python benchmark/simd_bit_benchmark.py --runs 8

Builds both fixtures with -Omax, runs them several times, verifies that the
intrinsic and naive paths produce the same checksum, and prints median
nanoseconds per call. The SIMD fixture is picked by host architecture:
simd_x86_64_vs_naive.fr on x86_64, simd_arm64_vs_naive.fr on arm64.
"""

from __future__ import annotations

import argparse
import platform
import statistics
import sys
import tempfile
from pathlib import Path

from _bench_common import BENCH, build_franko, pin_cpu, run_capture

SIMD_SOURCES = {
    "x86_64": (BENCH / "simd_x86_64_vs_naive.fr", "x86_64 SIMD vs naive Franko"),
    "arm64": (BENCH / "simd_arm64_vs_naive.fr", "ARM64 SIMD vs naive Franko"),
}
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


def run_binary(
    binary: Path, runs: int
) -> tuple[dict[str, tuple[float, float]], dict[str, int]]:
    samples: dict[str, list[tuple[int, int]]] = {}
    reps_by_name: dict[str, int] = {}

    for _ in range(runs):
        rows, checksums = parse_output(run_capture(binary))
        if checksums[0] != checksums[1]:
            raise RuntimeError(
                f"checksum mismatch for {binary.name}: "
                f"intrinsic={checksums[0]} naive={checksums[1]}"
            )
        for name, (simd_ns, naive_ns, row_reps) in rows.items():
            samples.setdefault(name, []).append((simd_ns, naive_ns))
            reps_by_name[name] = row_reps

    medians = {
        name: (
            statistics.median(item[0] for item in values) / reps_by_name[name],
            statistics.median(item[1] for item in values) / reps_by_name[name],
        )
        for name, values in samples.items()
    }
    return medians, reps_by_name


def print_table(
    title: str,
    medians: dict[str, tuple[float, float]],
    reps_by_name: dict[str, int],
) -> None:
    groups: dict[str | None, list[str]] = {}
    for name in medians:
        if "@" in name:
            base, _, size = name.partition("@")
            groups.setdefault(size, []).append(name)
        else:
            groups.setdefault(None, []).append(name)

    def size_key(size: str | None) -> tuple[int, str]:
        if size is None:
            return (-1, "")
        try:
            return (0, f"{int(size):010d}")
        except ValueError:
            return (0, size)

    for size in sorted(groups, key=size_key):
        names = groups[size]
        reps = reps_by_name[names[0]]
        print()
        if size is None:
            print(f"### {title}")
        else:
            print(f"### {title} ({size}-byte buffers)")
        print()
        print(f"Median of runs, {reps:,} calls per case.")
        print()
        print("| function | intrinsic ns/op | naive ns/op | speedup |")
        print("| --- | ---: | ---: | ---: |")
        for name in names:
            intrinsic, naive = medians[name]
            base = name.partition("@")[0] if size is not None else name
            print(f"| `{base}` | {intrinsic:.3f} | {naive:.3f} "
                  f"| {naive / intrinsic:.2f}x |")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runs", type=int, default=5, help="timed runs per fixture")
    parser.add_argument("--cpu", type=int, default=None, help="pin to one CPU")
    parser.add_argument("--native", action="store_true",
                        help="build with -march=native (enables SSSE3/SSE4.1/AES paths)")
    args = parser.parse_args()

    if args.runs < 1:
        parser.error("--runs must be at least 1")

    machine = platform.machine().lower()
    if machine in ("x86_64", "amd64"):
        arch = "x86_64"
    elif machine in ("arm64", "aarch64"):
        arch = "arm64"
    else:
        print(
            f"error: unsupported architecture {machine!r}: expected x86_64 or arm64",
            file=sys.stderr,
        )
        return 1
    simd_source, simd_title = SIMD_SOURCES[arch]

    pin_cpu(args.cpu)
    print(f"host: {platform.platform()} ({machine})")
    print("build: Franko -Omax / C++ -O3, bounds checks stripped")

    with tempfile.TemporaryDirectory(prefix="franko-intrinsics-") as tmp:
        work = Path(tmp)
        simd_binary = build_franko(
            simd_source, f"simd-{arch}", work, mode="omax", native=args.native
        )
        bit_binary = build_franko(BIT_SOURCE, "bit", work, mode="omax",
                                  native=args.native)

        simd_medians, simd_reps = run_binary(simd_binary, args.runs)
        bit_medians, bit_reps = run_binary(bit_binary, args.runs)

    print_table(simd_title, simd_medians, simd_reps)
    print_table("franko/bit vs naive Franko", bit_medians, bit_reps)
    print()
    print("Intrinsic and naive checksums matched for every run.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
