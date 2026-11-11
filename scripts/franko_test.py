"""Shared fixture runner for the Franko test suites."""

from __future__ import annotations

import difflib
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

from franko_tools import ROOT, executable_suffix, relative

from compile import franko_compile, native_compile


@dataclass(frozen=True)
class Case:
    src: Path
    cpp: Path
    log: Path
    bin: Path
    actual: Path
    expected: Path
    errors: Path
    warnings: Path
    forbidden: Path
    flags: Path
    args: Path
    stdin: Path


def case_paths(src: Path, tag: str = "", bin_name: str = ".bin") -> Case:
    base = src.with_suffix("")

    def output(ext: str) -> Path:
        return Path(f"{base}{tag}{ext}")

    def fixture(ext: str) -> Path:
        return Path(f"{base}{ext}")

    return Case(
        src=src,
        cpp=output(".cpp"),
        log=output(".compile.log"),
        bin=output(f"{bin_name}{executable_suffix()}"),
        actual=output(".actual"),
        expected=fixture(".expected"),
        errors=fixture(".errors"),
        warnings=fixture(".warnings"),
        forbidden=fixture(".forbidden"),
        flags=fixture(".flags"),
        args=fixture(".args"),
        stdin=fixture(".stdin"),
    )


def read_text(path: Path) -> str:
    with path.open("r", encoding="utf-8-sig", errors="replace", newline=None) as handle:
        return handle.read()


def _read_lines(path: Path) -> list[str]:
    return read_text(path).split("\n") if path.is_file() else []


def read_flags(path: Path) -> list[str]:
    flags: list[str] = []
    for line in _read_lines(path):
        stripped = line.strip()
        if stripped and not stripped.startswith("#"):
            flags.extend(stripped.split())
    return flags


def read_run_args(path: Path) -> list[str]:
    if not path.is_file():
        return []
    lines = read_text(path).split("\n")
    if lines and lines[-1] == "":
        lines.pop()
    return lines


def read_stdin_bytes(path: Path) -> bytes | None:
    return path.read_bytes().replace(b"\r", b"") if path.is_file() else None


def remove_artifacts(*paths: Path) -> None:
    for path in paths:
        try:
            path.unlink()
        except OSError:
            pass


def dump_file(path: Path | None) -> None:
    if path is None or not path.is_file():
        return
    text = read_text(path)
    sys.stdout.write(text)
    if text and not text.endswith("\n"):
        sys.stdout.write("\n")


def check_substrings(expected: Path, observed: str) -> bool:
    haystack = observed.replace("\\", "/")
    ok = True
    for line in _read_lines(expected):
        text = line.strip()
        if text and not text.startswith("#") and text.replace("\\", "/") not in haystack:
            print(f"expected text not found in output: {text}")
            ok = False
    return ok


def check_no_substrings(forbidden: Path, observed: str) -> bool:
    haystack = observed.replace("\\", "/")
    ok = True
    for line in _read_lines(forbidden):
        text = line.strip()
        if text and not text.startswith("#") and text.replace("\\", "/") in haystack:
            print(f"forbidden text found in output: {text}")
            ok = False
    return ok


def compare_output(actual: Path, expected: Path) -> bool:
    if not expected.is_file():
        print(f"expected file not found: {relative(expected)}")
        return False

    actual_lines = read_text(actual).split("\n")
    expected_lines = read_text(expected).split("\n")
    if actual_lines == expected_lines:
        return True

    print(f"---- output mismatch: {relative(expected)} vs {relative(actual)} ----")
    diff = list(difflib.unified_diff(
        expected_lines,
        actual_lines,
        fromfile=relative(expected),
        tofile=relative(actual),
        lineterm="",
    ))
    for line in diff[:200]:
        print(line)
    if len(diff) > 200:
        print("... (diff truncated)")
    return False


def run_test_binary(
    binary: Path,
    args: list[str],
    stdin_bytes: bytes | None,
    actual: Path,
) -> int:
    with actual.open("wb") as handle:
        completed = subprocess.run(
            [str(binary), *args],
            cwd=ROOT,
            input=b"" if stdin_bytes is None else stdin_bytes,
            stdout=handle,
            stderr=subprocess.STDOUT,
            check=False,
        )
    return completed.returncode


class Results:
    def __init__(self) -> None:
        self.passed = 0
        self.failed = 0
        self.skipped = 0

    def record(self, label: str, error: str | None = None) -> None:
        if error:
            self.failed += 1
            print(f"FAIL: {label} ({error})")
        else:
            self.passed += 1
            print(f"PASS: {label}")

    def skip(self, label: str, reason: str) -> None:
        self.skipped += 1
        print(f"SKIP: {label} ({reason})")

    def finish(self, success_message: str) -> int:
        print()
        print(
            f"passed: {self.passed}  failed: {self.failed}  "
            f"skipped: {self.skipped}"
        )
        print()
        if self.failed:
            return 1
        print(success_message)
        return 0


def _dump(title: str, path: Path | None) -> None:
    print(f"---- {title}: ----")
    dump_file(path)


def run_case(
    case: Case,
    *,
    mode: str,
    kind: str = "success",
    capture: bool = True,
    keep_cpp: bool = False,
) -> str | None:
    """Run one fixture and return None on success or a failure reason."""
    print(f"=== {relative(case.src)} ===")
    log = case.log if capture else None

    code = franko_compile(
        case.src,
        case.cpp,
        mode=mode,
        extra_flags=read_flags(case.flags),
        log=log,
        echo=not capture,
    )
    log_text = read_text(log) if log is not None else ""

    if kind == "failure":
        if code == 0:
            _dump("compiler output (accepted a failure program)", log)
            return "accepted a failure program"
        if case.errors.is_file() and not check_substrings(case.errors, log_text):
            _dump("compiler output (missing expected diagnostics)", log)
            return "missing expected diagnostics"
        remove_artifacts(case.cpp, case.log)
        return None

    if code != 0:
        _dump("compiler output", log)
        return "Franko compile failed"

    if log is not None and case.warnings.is_file() and not check_substrings(
        case.warnings, log_text
    ):
        _dump("compiler output (missing expected warnings)", log)
        return "missing expected warnings"

    if log is not None and case.forbidden.is_file() and not check_no_substrings(
        case.forbidden, log_text
    ):
        _dump("compiler output (emitted forbidden warnings)", log)
        return "emitted forbidden warnings"

    code = native_compile(case.cpp, case.bin, mode=mode, log=log, echo=not capture)
    if code != 0:
        _dump("C++ compiler output", log)
        return "C++ compile failed"

    if not case.bin.is_file() or case.bin.stat().st_size == 0:
        return "linked binary is empty, 0 bytes"

    exit_code = run_test_binary(
        case.bin,
        read_run_args(case.args),
        read_stdin_bytes(case.stdin),
        case.actual,
    )

    if kind == "runtime":
        if exit_code == 0:
            _dump("program output (expected a runtime failure, got exit 0)", case.actual)
            return "expected a runtime failure, exit 0"
    else:
        if exit_code != 0:
            print(f"---- runtime output (exit code {exit_code}): ----")
            dump_file(case.actual)
            return "binary exited non-zero"
        if not compare_output(case.actual, case.expected):
            return "output mismatch"

    remove_artifacts(
        *([case.cpp] if keep_cpp else []),
        case.log,
        case.bin,
        case.actual,
    )
    return None


def run_and_record(
    results: Results,
    src: Path,
    *,
    mode: str,
    kind: str = "success",
    tag: str = "",
    bin_name: str = ".bin",
    capture: bool = True,
    keep_cpp: bool = False,
) -> None:
    label = f"{relative(src)} [{mode}]"
    results.record(
        label,
        run_case(
            case_paths(src, tag, bin_name),
            mode=mode,
            kind=kind,
            capture=capture,
            keep_cpp=keep_cpp,
        ),
    )
