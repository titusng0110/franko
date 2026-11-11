#!/usr/bin/env python3
"""Extended red-black benchmark: Franko vs C++.

    uv run python benchmark/redblack_benchmark.py 10 --cpu 2

Four groups: Franko with NextAllocator, Franko with SparseSetAllocator,
C++ std::set, and a hand-written C++ array-pool control. Every group is
built portable and with -march=native; Franko and pool controls also get
ASan, checked-release, and unchecked-release variants.
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
    pin_cpu,
    run_timed,
    summarize,
)

FRANKO_SRC = BENCH / "redblack_franko.fr"
FRANKO_SPARSE_SRC = BENCH / "redblack_franko_sparse.fr"
CPP_SRC = BENCH / "redblack_cpp.cpp"
CPP_POOL_SRC = BENCH / "redblack_cpp_pool.cpp"


@dataclass(frozen=True)
class Mode:
    label: str
    group: str
    source: Path
    kind: str = "franko"       # "franko" | "cpp"
    mode: str = "opt"          # "asan" | "opt" | "omax"
    optimize: str | None = None
    native: bool = False


MODES = (
    Mode("sparse-default-native", "sparse", FRANKO_SPARSE_SRC,
         mode="asan", native=True),
    Mode("sparse-O-native", "sparse", FRANKO_SPARSE_SRC, native=True),
    Mode("sparse-Omax-native", "sparse", FRANKO_SPARSE_SRC,
         mode="omax", native=True),
    Mode("sparse-Omax", "sparse", FRANKO_SPARSE_SRC, mode="omax"),
    Mode("sparse-O", "sparse", FRANKO_SPARSE_SRC),
    Mode("sparse-default", "sparse", FRANKO_SPARSE_SRC, mode="asan"),

    Mode("default-native", "franko", FRANKO_SRC, mode="asan", native=True),
    Mode("-O-native", "franko", FRANKO_SRC, native=True),
    Mode("-Omax-native", "franko", FRANKO_SRC, mode="omax", native=True),
    Mode("-Omax", "franko", FRANKO_SRC, mode="omax"),
    Mode("-O", "franko", FRANKO_SRC),
    Mode("default", "franko", FRANKO_SRC, mode="asan"),

    Mode("cpp-O1-asan-native", "cpp", CPP_SRC, kind="cpp",
         mode="asan", optimize="1", native=True),
    Mode("cpp-O1-native", "cpp", CPP_SRC, kind="cpp",
         optimize="1", native=True),
    Mode("cpp-O3-native", "cpp", CPP_SRC, kind="cpp", native=True),
    Mode("cpp-O1-asan", "cpp", CPP_SRC, kind="cpp",
         mode="asan", optimize="1"),
    Mode("cpp-O1", "cpp", CPP_SRC, kind="cpp", optimize="1"),
    Mode("cpp-O3", "cpp", CPP_SRC, kind="cpp"),

    Mode("cpp-pool-O1-asan-native", "pool", CPP_POOL_SRC, kind="cpp",
         mode="asan", optimize="1", native=True),
    Mode("cpp-pool-O1-native", "pool", CPP_POOL_SRC, kind="cpp",
         optimize="1", native=True),
    Mode("cpp-pool-O3-native", "pool", CPP_POOL_SRC, kind="cpp", native=True),
    Mode("cpp-pool-O1-asan", "pool", CPP_POOL_SRC, kind="cpp",
         mode="asan", optimize="1"),
    Mode("cpp-pool-O1", "pool", CPP_POOL_SRC, kind="cpp", optimize="1"),
    Mode("cpp-pool-O3", "pool", CPP_POOL_SRC, kind="cpp"),
)

REPORT_ORDER = {
    "franko": (
        "default", "default-native",
        "-O", "-O-native",
        "-Omax", "-Omax-native",
    ),
    "sparse": (
        "sparse-default", "sparse-default-native",
        "sparse-O", "sparse-O-native",
        "sparse-Omax", "sparse-Omax-native",
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
    "sparse": "Franko (sparse-set pool, generated C++)",
    "cpp": "C++ std::set reference",
    "pool": "C++ array-pool control (same data structure as Franko)",
}


def build(mode: Mode, out_dir: Path) -> Path:
    if mode.kind == "franko":
        return build_franko(
            mode.source, mode.label, out_dir, mode=mode.mode, native=mode.native
        )
    return build_cpp(
        mode.source,
        mode.label,
        out_dir,
        optimize=mode.optimize or "3",
        sanitize=mode.mode == "asan",
        native=mode.native,
    )


def print_group_table(group: str, times: dict[str, list[float]],
                      sizes: dict[str, int]) -> None:
    print()
    print(f"### {GROUP_TITLE[group]}")
    print("| test | mean (s) | median | min | max | SD | CV | bin (KB) |")
    print("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
    for label in REPORT_ORDER[group]:
        stats = summarize(times[label])
        print(
            f"| {label} | {stats['mean']:.3f} | {stats['median']:.3f} | "
            f"{stats['min']:.3f} | {stats['max']:.3f} | "
            f"{stats['sd']:.3f} | {stats['cv']:.1f}% | "
            f"{sizes[label] / 1024:.0f} |"
        )


def print_raw_table(group: str, reps: int, times: dict[str, list[float]],
                    sizes: dict[str, int]) -> None:
    print()
    print(f"**{GROUP_TITLE[group]}**")
    print("| test | " + " | ".join(f"r{i + 1}" for i in range(reps))
          + " | mean | bin (KB) |")
    print("| --- | " + " | ".join("---" for _ in range(reps))
          + " | ---: | ---: |")
    for label in REPORT_ORDER[group]:
        values = times[label]
        row = " | ".join(f"{value:.3f}" for value in values)
        print(f"| {label} | {row} | {statistics.fmean(values):.3f} | "
              f"{sizes[label] / 1024:.0f} |")


def fmt_speedup(a: str, b: str, means: dict[str, float]) -> tuple[str, float]:
    ma, mb = means[a], means[b]
    if ma <= mb:
        return a, mb / ma if ma else float("inf")
    return b, ma / mb if mb else float("inf")


def print_comparison_table(title: str, pairs: list[tuple[str, str, str]],
                           means: dict[str, float]) -> None:
    print()
    print(f"### {title}")
    print("| comparison | faster | speedup | note |")
    print("| --- | --- | ---: | --- |")
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

    pin_cpu(args.cpu)
    labels = [mode.label for mode in MODES]
    run_order = list(labels)
    rng = random.Random(args.seed)

    with tempfile.TemporaryDirectory(prefix="franko-bench-") as tmp:
        out_dir = Path(tmp)
        binaries = {mode.label: build(mode, out_dir) for mode in MODES}
        sizes = {label: path.stat().st_size for label, path in binaries.items()}

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
        group_outputs = {
            group: {outputs[label] for label in REPORT_ORDER[group]}
            for group in REPORT_ORDER
        }
        for group, outs in group_outputs.items():
            for out in sorted(outs):
                print(f"[{group}] {out!r}")

        checks = {
            "franko outputs agree": len(group_outputs["franko"]) == 1,
            "franko sparse outputs agree": len(group_outputs["sparse"]) == 1,
            "cpp std::set outputs agree": len(group_outputs["cpp"]) == 1,
            "cpp array-pool outputs agree": len(group_outputs["pool"]) == 1,
            "franko sparse output matches franko":
                group_outputs["sparse"] == group_outputs["franko"],
            "cpp array-pool output matches franko":
                group_outputs["pool"] == group_outputs["franko"],
        }
        for name, ok in checks.items():
            print(f"{name}: {ok}")
        if not all(checks.values()):
            return 1

        for group in REPORT_ORDER:
            print_group_table(group, times, sizes)

        means = {label: statistics.fmean(times[label]) for label in labels}

        print()
        print("### -march=native effect")
        print("| base build | portable (s) | native (s) | native delta |")
        print("| --- | ---: | ---: | ---: |")
        native_pairs = [
            ("default", "default-native"),
            ("-O", "-O-native"),
            ("-Omax", "-Omax-native"),
            ("sparse-default", "sparse-default-native"),
            ("sparse-O", "sparse-O-native"),
            ("sparse-Omax", "sparse-Omax-native"),
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

        print_comparison_table("Franko sparse-set build-mode effects", [
            ("sparse-default", "sparse-O", "portable"),
            ("sparse-default-native", "sparse-O-native", "native"),
            ("sparse-O", "sparse-Omax", "portable"),
            ("sparse-O-native", "sparse-Omax-native", "native"),
        ], means)

        print_comparison_table("Franko allocator variants", [
            ("default", "sparse-default", "portable, ASan"),
            ("default-native", "sparse-default-native", "native, ASan"),
            ("-O", "sparse-O", "portable, checked release"),
            ("-O-native", "sparse-O-native", "native, checked release"),
            ("-Omax", "sparse-Omax", "portable, unchecked release"),
            ("-Omax-native", "sparse-Omax-native", "native, unchecked release"),
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
            ("-O", "cpp-O1", "Franko -O3 vs C++ -O1"),
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
            ("default-native", "cpp-pool-O1-asan-native",
             "same, native, ASan"),
        ], means)

        print()
        print("### Raw measured runs")
        for group in REPORT_ORDER:
            print_raw_table(group, args.reps, times, sizes)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
