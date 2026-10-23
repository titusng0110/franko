Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ============================================================
# Franko spec tests (Windows)
# ============================================================
#
# Runs the full spec test suite:
#
#   tests/failure/<family>/<case>.fr
#     Must be REJECTED by the Franko compiler (non-zero exit).
#     Optional <case>.errors: every non-empty line (lines starting
#     with '#' are comments) must appear as a substring of the
#     compiler output, pinning the expected diagnostics.
#     Optional <case>.flags: every non-empty, non-comment line is
#     word-split and appended to the compiler invocation.
#     Helper files may live deeper (tests/failure/<family>/lib/);
#     they are imported by a fixture and are not run standalone.
#
#   tests/success/<concept>/main.fr
#     Must compile, run, and produce stdout matching main.expected.
#     Optional main.warnings: every non-empty line must appear as a
#     substring of the compiler output, pinning expected warnings.
#     Optional main.flags: extra compiler flags, same format as above.
#
#   tests/runtime/<family>/<case>.fr
#     Must compile, but the binary must EXIT NON-ZERO when run. Used
#     for documented runtime aborts such as uninitialized dynamic
#     array access. Runtime messages are intentionally not pinned:
#     they may change without breaking the suite.
#
# Success golden outputs must be deterministic. Never put values whose
# rendering varies across runs or platforms (such as live addresses)
# in a .expected file; cover those through the runtime category, which
# is judged by exit status only.
#
# Generated artifacts (.cpp, .bin, .actual, compiler logs) are removed
# after a passing case and kept for inspection after a failure.
#
# Requires: java.exe and g++.exe on PATH, plus compiled compiler
# classes (run .\update_windows-x64.ps1 first).
#
# Usage:
#   .\tests\run_specs_tests.ps1
# ============================================================

$ROOT = Split-Path -Parent $PSScriptRoot
Set-Location $ROOT

# The Franko compiler reads ProgramTemplate.cpp relative to the cwd.
$CP = ".;lib/antlr-4.13.2-complete.jar;build/generated;build/classes"
$JEM_INC = "third_party/jemalloc/windows-x64/include"
$JEM_LIB = "third_party/jemalloc/windows-x64/lib/jemalloc.lib"

# Ensure compiled Java compiler classes exist.
if (-not (Test-Path -LiteralPath (Join-Path $ROOT "build/classes/Main.class"))) {
    Write-Error "Compiler toolchain not built: $ROOT\build\classes\Main.class missing. Run .\update_windows-x64.ps1 first."
    exit 1
}

$gxx = Get-Command "g++.exe" -ErrorAction SilentlyContinue
if ($null -eq $gxx) {
    throw "g++.exe not found on PATH. Add your MinGW64 bin directory to PATH first."
}

$script:passed = 0
$script:failed = 0

function Fail-Case($message) {
    Write-Host "FAIL: $message"
    $script:failed++
}

function Pass-Case($message) {
    Write-Host "PASS: $message"
    $script:passed++
}

# Every non-empty, non-comment line of $expectedFile must appear as a
# literal substring of $observedText (mirrors check_substrings in
# tests/run_specs_tests.sh).
function Test-Substrings($expectedFile, $observedText) {
    foreach ($line in (Get-Content -LiteralPath $expectedFile)) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith('#')) { continue }
        if ($observedText.IndexOf($trimmed, [StringComparison]::Ordinal) -lt 0) {
            Write-Host "expected text not found in output: $trimmed"
            return $false
        }
    }
    return $true
}

# Reads a .flags file (each non-empty, non-comment line is
# word-split) and returns the extra compiler arguments as an array.
function Read-ExtraFlags($flagsFile) {
    $flags = @()
    if (Test-Path -LiteralPath $flagsFile) {
        foreach ($line in (Get-Content -LiteralPath $flagsFile)) {
            if ([string]::IsNullOrWhiteSpace($line)) { continue }
            $trimmed = $line.Trim()
            if ($trimmed.StartsWith('#')) { continue }
            $flags += $trimmed -split '\s+'
        }
    }
    return $flags
}

# Compares two texts after CRLF normalization. Prints the first
# differing lines and returns $true when they match.
function Test-OutputMatch($actualText, $expectedText) {
    $a = ($actualText -replace "`r`n", "`n") -split "`n"
    $e = ($expectedText -replace "`r`n", "`n") -split "`n"

    $count = [Math]::Max($a.Count, $e.Count)
    $shown = 0
    for ($i = 0; $i -lt $count; $i++) {
        $al = if ($i -lt $a.Count) { $a[$i] } else { "<missing>" }
        $el = if ($i -lt $e.Count) { $e[$i] } else { "<missing>" }
        if ($al -cne $el) {
            Write-Host ("line {0}: expected <{1}> actual <{2}>" -f ($i + 1), $el, $al)
            $shown++
            if ($shown -ge 20) { break }
        }
    }

    return ($shown -eq 0) -and ($a.Count -eq $e.Count)
}

function Remove-Artifacts($paths) {
    Remove-Item -Force -LiteralPath $paths -ErrorAction SilentlyContinue
}

# ------------------------------------------------------------
# Failure cases: the compiler must reject the program.
# ------------------------------------------------------------
$failureCases = Get-ChildItem -Path "tests/failure/*/*.fr" -File |
    Sort-Object Name |
    ForEach-Object { $_.FullName }

foreach ($src in $failureCases) {
    $base = $src.Substring(0, $src.Length - 3)
    $log = "$base.compile.log"
    $errs = "$base.errors"
    $extraFlags = Read-ExtraFlags "$base.flags"

    Write-Host "=== $src ==="

    java -cp $CP Main $src -o "$base.cpp" @extraFlags > $log 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "---- compiler output (accepted a failure program): ----"
        Get-Content -LiteralPath $log | Write-Host
        Remove-Artifacts @("$base.cpp", $log)
        Fail-Case $src
        continue
    }

    if ((Test-Path -LiteralPath $errs) -and -not (Test-Substrings $errs ([IO.File]::ReadAllText($log)))) {
        Write-Host "---- compiler output (missing expected diagnostics): ----"
        Get-Content -LiteralPath $log | Write-Host
        Fail-Case "$src (missing expected diagnostics)"
        continue
    }

    Remove-Artifacts @($log, "$base.cpp")
    Pass-Case $src
}

# ------------------------------------------------------------
# Success cases: compile, run, compare stdout with main.expected.
# ------------------------------------------------------------
$successCases = Get-ChildItem -Path "tests/success/*/main.fr" -File |
    Sort-Object Name |
    ForEach-Object { $_.FullName }

foreach ($src in $successCases) {
    $dir = Split-Path -Parent $src
    $base = Join-Path $dir "main"
    $outCpp = "$base.cpp"
    $outBin = "$base.bin"
    $actual = "$base.actual"
    $log = "$base.compile.log"
    $expected = "$base.expected"
    $warn = "$base.warnings"
    $extraFlags = Read-ExtraFlags "$base.flags"

    Write-Host "=== $src ==="

    java -cp $CP Main $src -o $outCpp @extraFlags > $log 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "---- compiler output: ----"
        Get-Content -LiteralPath $log | Write-Host
        Fail-Case "$src (Franko compile failed)"
        continue
    }

    if ((Test-Path -LiteralPath $warn) -and -not (Test-Substrings $warn ([IO.File]::ReadAllText($log)))) {
        Write-Host "---- compiler output (missing expected warnings): ----"
        Get-Content -LiteralPath $log | Write-Host
        Fail-Case "$src (missing expected warnings)"
        continue
    }

    & g++ -O3 -std=c++14 -Wall -Wextra -Wpedantic -Wshadow `
        "-I$ROOT\include" "-I$JEM_INC" `
        $outCpp $JEM_LIB `
        -static-libstdc++ -static-libgcc `
        -o $outBin > $log 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "---- g++ output: ----"
        Get-Content -LiteralPath $log | Write-Host
        Fail-Case "$src (C++ compile failed)"
        continue
    }

    & $outBin > $actual 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "---- runtime output: ----"
        Get-Content -LiteralPath $actual | Write-Host
        Fail-Case "$src (binary exited non-zero)"
        continue
    }

    if (-not (Test-OutputMatch ([IO.File]::ReadAllText($actual)) ([IO.File]::ReadAllText($expected)))) {
        Fail-Case "$src (output mismatch)"
        continue
    }

    Remove-Artifacts @($outCpp, $outBin, $log, $actual)
    Pass-Case $src
}

# ------------------------------------------------------------
# Runtime cases: the program compiles but must abort at run time.
# ------------------------------------------------------------
$runtimeCases = Get-ChildItem -Path "tests/runtime/*/*.fr" -File |
    Sort-Object Name |
    ForEach-Object { $_.FullName }

foreach ($src in $runtimeCases) {
    $base = $src.Substring(0, $src.Length - 3)
    $outCpp = "$base.cpp"
    $outBin = "$base.bin"
    $actual = "$base.actual"
    $log = "$base.compile.log"
    $extraFlags = Read-ExtraFlags "$base.flags"

    Write-Host "=== $src ==="

    java -cp $CP Main $src -o $outCpp @extraFlags > $log 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "---- compiler output: ----"
        Get-Content -LiteralPath $log | Write-Host
        Fail-Case "$src (Franko compile failed; runtime cases must compile)"
        continue
    }

    & g++ -O3 -std=c++14 `
        "-I$ROOT\include" "-I$JEM_INC" `
        $outCpp $JEM_LIB `
        -static-libstdc++ -static-libgcc `
        -o $outBin > $log 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "---- g++ output: ----"
        Get-Content -LiteralPath $log | Write-Host
        Fail-Case "$src (C++ compile failed)"
        continue
    }

    & $outBin > $actual 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "---- program output (expected a runtime failure, got exit 0): ----"
        Get-Content -LiteralPath $actual | Write-Host
        Remove-Artifacts @($outCpp, $outBin, $log, $actual)
        Fail-Case "$src (expected a runtime failure, exit 0)"
        continue
    }

    Remove-Artifacts @($outCpp, $outBin, $log, $actual)
    Pass-Case $src
}

Write-Host ""
Write-Host ("passed: {0}  failed: {1}" -f $script:passed, $script:failed)

if ($script:failed -ne 0) {
    exit 1
}

Write-Host "All spec tests passed."
