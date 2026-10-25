Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ============================================================
# Franko library tests (Windows)
# ============================================================
#
# Tests the importable Franko standard-library modules in
# library_tests/ (tests/ covers the language itself and never
# imports library modules).
#
# Layout:
#
#   library_tests/<module>/<suite>/success/<case>/main.fr
#     Must compile, run, and produce stdout matching main.expected.
#     Optional main.warnings: every non-empty line must appear as a
#     substring of the compiler output, pinning expected warnings.
#     Optional main.flags: extra compiler flags, one per line
#     (word-split like the spec suite).
#     Optional main.args: each line is one command-line argument
#     passed verbatim to the binary (empty lines pass empty-string
#     arguments; a trailing newline does not add an argument).
#     Optional main.stdin: bytes fed to the binary on stdin
#     (carriage returns are stripped, so CRLF checkouts behave
#     like LF ones).
#
#   library_tests/<module>/<suite>/failure/<case>/main.fr
#     Must be REJECTED by the Franko compiler (non-zero exit).
#     Optional main.errors: every non-empty line (lines starting
#     with '#' are comments) must appear as a substring of the
#     compiler output, pinning the expected diagnostics.
#     Optional main.flags: extra compiler flags, same format.
#
#   library_tests/<module>/<suite>/runtime/<case>/main.fr
#     Must compile, but the binary must EXIT NON-ZERO when run. Used
#     for documented runtime aborts such as FFI buffer-bound panics.
#     Runtime messages are intentionally not pinned: they may change
#     without breaking the suite. Optional main.flags, main.args,
#     and main.stdin as in the success category.
#
# Platform suites:
#
#   A suite directory named exactly `win32` runs on Windows only and
#   is skipped everywhere else (the os win32 File API bridge is a
#   compile-time #error off Windows). Every other suite runs on all
#   platforms.
#
# Success golden outputs must be deterministic. Never put values whose
# rendering varies across runs or platforms (such as live addresses)
# in a .expected file; cover those through the runtime category, which
# is judged by exit status only.
#
# Generated artifacts (main.cpp, main.bin.exe, main.actual, compiler
# logs) are removed after a passing case and kept for inspection after
# a failure.
#
# Test binaries use a .exe extension so Windows can execute them
# directly and Explorer knows how to open them.
#
# Requires: java.exe and g++.exe on PATH, plus compiled compiler
# classes (run .\update_windows-x64.ps1 first).
#
# Usage:
#   .\library_tests\run_library_tests.ps1
# ============================================================

$ROOT = Split-Path -Parent $PSScriptRoot
Set-Location $ROOT

# The Franko compiler reads ProgramTemplate.cpp relative to the cwd.
$CP = ".;lib/antlr-4.13.2-complete.jar;build/generated;build/classes"
$JEM_INC = "third_party/jemalloc/windows-x64/include"
$JEM_LIB = "third_party/jemalloc/windows-x64/lib/jemalloc.lib"

# The import search directory is passed explicitly so library modules
# (e.g. "franko/os") resolve the same way regardless of cwd.
$IMPORT_DIR = Join-Path $ROOT "import"

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
$script:skipped = 0

function Fail-Case($message) {
    Write-Host "FAIL: $message"
    $script:failed++
}

function Pass-Case($message) {
    Write-Host "PASS: $message"
    $script:passed++
}

function Skip-Case($message) {
    Write-Host "SKIP: $message"
    $script:skipped++
}

# True when the win32-only suites may run on this machine.
function Test-IsWindows {
    return [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
}

function Test-IsWin32Suite($path) {
    return ($path -replace '\\', '/') -match '/win32/'
}

# Every non-empty, non-comment line of $expectedFile must appear as a
# literal substring of $observedText (mirrors check_substrings in
# library_tests/run_library_tests.sh).
#
# Compiler diagnostics embed filesystem paths, which use '\' on
# Windows but '/' everywhere else. Both sides are normalized to '/'
# first, so the checked-in main.errors/main.warnings files can pin
# paths in POSIX form on every platform.
function Test-Substrings($expectedFile, $observedText) {
    $observed = $observedText -replace '\\', '/'
    foreach ($line in (Get-Content -LiteralPath $expectedFile)) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith('#')) { continue }
        $needle = $trimmed -replace '\\', '/'
        if ($observed.IndexOf($needle, [StringComparison]::Ordinal) -lt 0) {
            Write-Host "expected text not found in output: $trimmed"
            return $false
        }
    }
    return $true
}

# Reads a main.flags file (each non-empty, non-comment line is
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

# Reads a main.args file and returns the command-line arguments as
# an array. Each line is one argument passed verbatim; empty lines
# pass empty-string arguments. A trailing newline does not add an
# argument, and an empty or missing file yields no arguments.
function Read-RunArgs($argsFile) {
    $runArgs = @()
    if (Test-Path -LiteralPath $argsFile) {
        $text = [IO.File]::ReadAllText($argsFile) -replace "`r`n", "`n"
        if (-not [string]::IsNullOrEmpty($text)) {
            $parts = $text -split "`n"
            if ($text.EndsWith("`n")) {
                $parts = $parts[0..($parts.Count - 2)]
            }
            foreach ($p in $parts) { $runArgs += $p }
        }
    }
    return $runArgs
}

# Reads a main.stdin file and returns its bytes with carriage returns
# stripped (so CRLF checkouts behave like LF ones), or $null when the
# file does not exist. Piping the returned [byte[]] to a native
# binary feeds it unmodified: unlike strings, bytes pass through to
# native stdin with no added newline and no re-encoding.
function Read-StdInBytes($stdinFile) {
    if (-not (Test-Path -LiteralPath $stdinFile)) { return $null }
    $raw = [IO.File]::ReadAllBytes($stdinFile)
    return [byte[]]@($raw | Where-Object { $_ -ne 13 })
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

# Runs the test binary, feeding it main.stdin bytes when present.
# Returns the process exit code.
function Invoke-TestBinary($outBin, $runArgs, $stdinBytes, $actual) {
    if ($null -eq $stdinBytes) {
        & $outBin @runArgs > $actual 2>&1
    } else {
        $stdinBytes | & $outBin @runArgs > $actual 2>&1
    }
    return $LASTEXITCODE
}

# ------------------------------------------------------------
# Failure cases: the compiler must reject the program.
# ------------------------------------------------------------
$failureCases = Get-ChildItem -Path "library_tests/*/*/failure/*/main.fr" -File |
    Sort-Object Name |
    ForEach-Object { $_.FullName }

foreach ($src in $failureCases) {
    $dir = Split-Path -Parent $src
    $base = Join-Path $dir "main"
    $log = "$base.compile.log"
    $errs = "$base.errors"
    $extraFlags = Read-ExtraFlags "$base.flags"

    Write-Host "=== $src ==="

    if ((Test-IsWin32Suite $src) -and -not (Test-IsWindows)) {
        Skip-Case "$src (windows-only suite)"
        continue
    }

    java -cp $CP Main $src -o "$base.cpp" -p $IMPORT_DIR @extraFlags > $log 2>&1
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
$successCases = Get-ChildItem -Path "library_tests/*/*/success/*/main.fr" -File |
    Sort-Object Name |
    ForEach-Object { $_.FullName }

foreach ($src in $successCases) {
    $dir = Split-Path -Parent $src
    $base = Join-Path $dir "main"
    $outCpp = "$base.cpp"
    $outBin = "$base.bin.exe"
    $actual = "$base.actual"
    $log = "$base.compile.log"
    $expected = "$base.expected"
    $warn = "$base.warnings"
    $extraFlags = Read-ExtraFlags "$base.flags"
    $runArgs = @(Read-RunArgs "$base.args")
    $stdinBytes = Read-StdInBytes "$base.stdin"

    Write-Host "=== $src ==="

    if ((Test-IsWin32Suite $src) -and -not (Test-IsWindows)) {
        Skip-Case "$src (windows-only suite)"
        continue
    }

    java -cp $CP Main $src -o $outCpp -p $IMPORT_DIR @extraFlags > $log 2>&1
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

    if ((Get-Item -LiteralPath $outBin).Length -eq 0) {
        Fail-Case "$src (linked binary is empty, 0 bytes)"
        continue
    }

    $exitCode = Invoke-TestBinary $outBin $runArgs $stdinBytes $actual
    if ($exitCode -ne 0) {
        Write-Host "---- runtime output (exit code $exitCode): ----"
        Get-Content -LiteralPath $actual | Write-Host
        Fail-Case "$src (binary exited non-zero)"
        continue
    }

    if (-not (Test-OutputMatch ([IO.File]::ReadAllText($actual)) ([IO.File]::ReadAllText($expected)))) {
        $actualBytes = ([IO.File]::ReadAllBytes($actual)).Length
        Fail-Case "$src (output mismatch, exit=$exitCode, actual bytes=$actualBytes)"
        continue
    }

    Remove-Artifacts @($outCpp, $outBin, $log, $actual)
    Pass-Case $src
}

# ------------------------------------------------------------
# Runtime cases: the program compiles but must abort at run time.
# ------------------------------------------------------------
$runtimeCases = Get-ChildItem -Path "library_tests/*/*/runtime/*/main.fr" -File |
    Sort-Object Name |
    ForEach-Object { $_.FullName }

foreach ($src in $runtimeCases) {
    $dir = Split-Path -Parent $src
    $base = Join-Path $dir "main"
    $outCpp = "$base.cpp"
    $outBin = "$base.bin.exe"
    $actual = "$base.actual"
    $log = "$base.compile.log"
    $extraFlags = Read-ExtraFlags "$base.flags"
    $runArgs = @(Read-RunArgs "$base.args")
    $stdinBytes = Read-StdInBytes "$base.stdin"

    Write-Host "=== $src ==="

    if ((Test-IsWin32Suite $src) -and -not (Test-IsWindows)) {
        Skip-Case "$src (windows-only suite)"
        continue
    }

    java -cp $CP Main $src -o $outCpp -p $IMPORT_DIR @extraFlags > $log 2>&1
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

    if ((Get-Item -LiteralPath $outBin).Length -eq 0) {
        Fail-Case "$src (linked binary is empty, 0 bytes)"
        continue
    }

    $exitCode = Invoke-TestBinary $outBin $runArgs $stdinBytes $actual
    if ($exitCode -eq 0) {
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
Write-Host ("passed: {0}  failed: {1}  skipped: {2}" -f $script:passed, $script:failed, $script:skipped)

if ($script:failed -ne 0) {
    exit 1
}

Write-Host "All library tests passed."
exit 0
