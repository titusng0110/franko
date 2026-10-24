Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ============================================================
# Franko program smoke test (Windows)
# ============================================================
#
# Compiles every program in programs/, runs it, and compares its
# stdout against the checked-in <program>.expected file.
#
# Every program in programs/ is a positive test. Negative checker
# tests live in tests/ and are intentionally not part of this
# smoke test; run .\tests\run_specs_tests.ps1 for the full suite.
#
# Success golden outputs must be deterministic. Never put values
# whose rendering varies across runs or platforms (such as live
# addresses) in a .expected file.
#
# Generated artifacts (.cpp, .testbin.exe, .actual) are removed after a
# passing case and kept for inspection after a failure.
#
# The test binary uses a .exe extension (rather than extensionless or
# .testbin) so Windows can execute it directly and Explorer knows how
# to open it.
#
# Requires: java.exe and g++.exe on PATH, plus compiled compiler
# classes (run .\update_windows-x64.ps1 first).
#
# Usage:
#   .\programs\run_programs.ps1
# ============================================================

$ROOT = Split-Path -Parent $PSScriptRoot
Set-Location $ROOT

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

# The import search directory is passed explicitly so programs that
# import library modules (e.g. programs/stack.fr importing
# "franko/vector") resolve the same way regardless of cwd.
$IMPORT_DIR = Join-Path $ROOT "import"

$passed = 0
$failed = 0

function Fail-Case($message) {
    Write-Host "FAIL: $message"
    $script:failed++
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

$programs = Get-ChildItem -Path "programs" -Filter "*.fr" -File |
    Sort-Object Name |
    ForEach-Object { $_.FullName }

if ($programs.Count -eq 0) {
    throw "No .fr programs found under programs/."
}

foreach ($src in $programs) {
    $base = $src.Substring(0, $src.Length - 3)
    $name = [IO.Path]::GetFileNameWithoutExtension($src)
    $outCpp = "$base.cpp"
    $outBin = "$base.testbin.exe"
    $actual = "$base.actual"
    $expected = "$base.expected"

    Write-Host ""
    Write-Host "=== $src ==="
    Write-Host ""

    # Excluded on Windows: string.fr — a CRLF checkout changes the
    # program's output.
    if ($name -eq "string") {
        Write-Host "Skipping $src on Windows: a CRLF checkout changes the program's output."
        continue
    }

    java -cp $CP Main $src -o $outCpp -p $IMPORT_DIR
    if ($LASTEXITCODE -ne 0) {
        Fail-Case "$src (Franko compile failed)"
        continue
    }

    & g++ -O3 -std=c++14 -Wall -Wextra -Wpedantic -Wshadow `
        "-I$ROOT\include" "-I$JEM_INC" `
        $outCpp $JEM_LIB `
        -static-libstdc++ -static-libgcc `
        -o $outBin
    if ($LASTEXITCODE -ne 0) {
        Fail-Case "$src (C++ compile failed)"
        continue
    }

    $binSize = (Get-Item -LiteralPath $outBin).Length
    if ($binSize -eq 0) {
        Fail-Case "$src (linked binary is empty, 0 bytes)"
        continue
    }

    & $outBin > $actual 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        Write-Host "---- runtime output (exit code $exitCode): ----"
        Get-Content -LiteralPath $actual | Write-Host
        Fail-Case "$src (binary exited non-zero)"
        continue
    }

    Write-Host "Comparing output against $expected`:"
    $actualText = [IO.File]::ReadAllText($actual)
    $expectedText = [IO.File]::ReadAllText($expected)
    if (-not (Test-OutputMatch $actualText $expectedText)) {
        $actualBytes = ([IO.File]::ReadAllBytes($actual)).Length
        Fail-Case "$src (output mismatch, exit=$exitCode, actual bytes=$actualBytes)"
        continue
    }

    Write-Host ""
    Write-Host "PASS: $src ran successfully, output matches $expected"
    Remove-Item -Force -LiteralPath $outCpp, $outBin, $actual -ErrorAction SilentlyContinue
    $passed++
}

Write-Host ""
Write-Host "passed: $passed  failed: $failed"

if ($failed -ne 0) {
    exit 1
}

Write-Host "All programs passed."

# Pin the process exit code explicitly so the step host cannot report
# a stale $LASTEXITCODE from an earlier native invocation instead of
# the actual all-programs-passed outcome.
exit 0
