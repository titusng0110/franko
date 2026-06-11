Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

trap {
    [Console]::Error.WriteLine("Error: command failed: $($_.InvocationInfo.Line.Trim())")
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
}

# Resolve project root: script location
$ROOT = $PSScriptRoot

function RunNative {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Exe,

        [Parameter(Mandatory = $false)]
        [string[]] $Argv = @()
    )

    $display = @()
    $display += $Exe
    $display += $Argv

    Write-Host ($display -join " ")

    & $Exe @Argv

    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code $LASTEXITCODE`: $($display -join ' ')"
    }
}

if ($args.Count -lt 1) {
    Write-Error "Usage: .\compile.ps1 <source.fr>"
    exit 1
}

$SRC = $args[0]

if (-not (Test-Path -LiteralPath $SRC -PathType Leaf)) {
    Write-Error "Error: source file not found: $SRC"
    exit 1
}

# ------------------------------------------------------------
# Target
# ------------------------------------------------------------

$TARGET = "windows-x64"

# ------------------------------------------------------------
# Tool checks
# ------------------------------------------------------------

$GPP = Get-Command "g++.exe" -ErrorAction SilentlyContinue

if ($null -eq $GPP) {
    throw "g++.exe not found on PATH. Add your MinGW64 bin directory to PATH first."
}

$JAVA = Get-Command "java.exe" -ErrorAction SilentlyContinue

if ($null -eq $JAVA) {
    throw "java.exe not found on PATH."
}

# ------------------------------------------------------------
# ANTLR / Java compiler toolchain
# ------------------------------------------------------------

$ANTLR_JAR = Join-Path $ROOT "lib\antlr-4.13.2-complete.jar"

if (-not (Test-Path -LiteralPath $ANTLR_JAR -PathType Leaf)) {
    Write-Error "Error: ANTLR jar not found at $ANTLR_JAR"
    exit 1
}

$ANTLR_CP = ".;$ANTLR_JAR"

if ($env:CLASSPATH) {
    $ANTLR_CP = "$ANTLR_CP;$env:CLASSPATH"
}

$BUILD_DIR = Join-Path $ROOT "build"
$GEN_DIR   = Join-Path $BUILD_DIR "generated"
$CLS_DIR   = Join-Path $BUILD_DIR "classes"

# Ensure compiled classes exist.
# Checking Main.class is better than checking only that the directory exists.
$MAIN_CLASS = Join-Path $CLS_DIR "Main.class"

if (-not (Test-Path -LiteralPath $MAIN_CLASS -PathType Leaf)) {
    Write-Host "Build not found. Running toolchain..."

    $UPDATE_SCRIPT = Join-Path $ROOT "update.ps1"

    if (-not (Test-Path -LiteralPath $UPDATE_SCRIPT -PathType Leaf)) {
        throw "Toolchain update script not found: $UPDATE_SCRIPT"
    }

    $PSExe = if ($PSVersionTable.PSEdition -eq "Core") {
        "pwsh"
    } else {
        "powershell.exe"
    }

    RunNative -Exe $PSExe -Argv @(
        "-ExecutionPolicy", "Bypass",
        "-File", $UPDATE_SCRIPT
    )
}

# ------------------------------------------------------------
# Bundled jemalloc for Windows x64 / MinGW
# ------------------------------------------------------------

$JEMALLOC_DIR = Join-Path $ROOT "third_party\jemalloc\windows-x64"
$JEMALLOC_INCLUDE = Join-Path $JEMALLOC_DIR "include"
$JEMALLOC_HEADER = Join-Path $JEMALLOC_INCLUDE "jemalloc\jemalloc.h"
$JEMALLOC_LIB = Join-Path $JEMALLOC_DIR "lib\libjemalloc.a"

if (-not (Test-Path -LiteralPath $JEMALLOC_HEADER -PathType Leaf)) {
    throw @"
Bundled jemalloc header not found:
  $JEMALLOC_HEADER

Expected:
  third_party\jemalloc\windows-x64\include\jemalloc\jemalloc.h
"@
}

if (-not (Test-Path -LiteralPath $JEMALLOC_LIB -PathType Leaf)) {
    throw @"
Bundled jemalloc static library not found:
  $JEMALLOC_LIB

Expected:
  third_party\jemalloc\windows-x64\lib\libjemalloc.a
"@
}

# ------------------------------------------------------------
# Output paths
# ------------------------------------------------------------

$BASENAME = if ($SRC.EndsWith(".fr")) {
    $SRC.Substring(0, $SRC.Length - 3)
} else {
    $srcDir = [System.IO.Path]::GetDirectoryName($SRC)
    $srcName = [System.IO.Path]::GetFileNameWithoutExtension($SRC)

    if ([string]::IsNullOrEmpty($srcDir)) {
        $srcName
    } else {
        [System.IO.Path]::Combine($srcDir, $srcName)
    }
}

$CPP_OUT = "$BASENAME.cpp"
$BIN_OUT = "$BASENAME.exe"

Write-Host "Franko target: $TARGET"
Write-Host "Source: $SRC"
Write-Host "Generated C++: $CPP_OUT"
Write-Host "Binary output: $BIN_OUT"
Write-Host "jemalloc header: $JEMALLOC_HEADER"
Write-Host "jemalloc static library: $JEMALLOC_LIB"
Write-Host ""

# ------------------------------------------------------------
# Franko -> C++14
# ------------------------------------------------------------

RunNative -Exe "java" -Argv @(
    "-cp", "$ANTLR_CP;$CLS_DIR;$GEN_DIR",
    "Main",
    $SRC,
    "-o",
    $CPP_OUT
)

Write-Host ""

# ------------------------------------------------------------
# C++14 -> Windows executable
# ------------------------------------------------------------
#
# Notes:
#   -I$JEMALLOC_INCLUDE lets FrankoRuntime.hpp include:
#       <jemalloc/jemalloc.h>
#
#   $JEMALLOC_LIB statically links bundled jemalloc.
#
#   -lwinpthread is often needed by MinGW-built libraries.
#

$GppArgs = @(
    "-O3",
    "-std=c++14",
    "-Wall",
    "-Wextra",
    "-Wpedantic",
    "-Wshadow",
    "-I$ROOT\include",
    "-I$JEMALLOC_INCLUDE",
    $CPP_OUT,
    $JEMALLOC_LIB,
    "-lwinpthread",
    "-o",
    $BIN_OUT
)

RunNative -Exe "g++" -Argv $GppArgs

Write-Host ""
Write-Host "Successfully compiled to binary output: $BIN_OUT"
Write-Host ""
Write-Host "✅ Compilation finished with statically linked jemalloc."