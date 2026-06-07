Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

trap {
    Write-Error "Error: command failed: $($_.InvocationInfo.Line.Trim())"
    Write-Error $_
    exit 1
}

# Resolve project root: script location
$ROOT = Split-Path -Parent $MyInvocation.MyCommand.Path

function Run {
    param(
        [Parameter(Mandatory = $true, ValueFromRemainingArguments = $true)]
        [string[]] $Command
    )

    Write-Host ($Command -join " ")

    & $Command[0] @($Command[1..($Command.Count - 1)])

    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code $LASTEXITCODE`: $($Command -join ' ')"
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

# Ensure compiled classes exist
if (-not (Test-Path -LiteralPath $CLS_DIR -PathType Container)) {
    Write-Host "Build not found. Running toolchain..."

    $UPDATE_SCRIPT = Join-Path $ROOT "update.ps1"

    if (-not (Test-Path -LiteralPath $UPDATE_SCRIPT -PathType Leaf)) {
        throw "Toolchain update script not found: $UPDATE_SCRIPT"
    }

    Run powershell.exe -ExecutionPolicy Bypass -File $UPDATE_SCRIPT
}

$BASENAME = if ($SRC.EndsWith(".fr")) {
    $SRC.Substring(0, $SRC.Length - 3)
} else {
    [System.IO.Path]::Combine(
        [System.IO.Path]::GetDirectoryName($SRC),
        [System.IO.Path]::GetFileNameWithoutExtension($SRC)
    )
}

$CPP_OUT = "$BASENAME.cpp"
$BIN_OUT = "$BASENAME.out"

Write-Host "Source: $SRC"
Write-Host ""

Run java `
    -cp "$ANTLR_CP;$CLS_DIR;$GEN_DIR" `
    Main `
    $SRC `
    -o `
    $CPP_OUT

Write-Host ""

Run g++ `
    -O3 `
    -std=c++14 `
    -Wall `
    -Wextra `
    -Wpedantic `
    -Wshadow `
    "-I$ROOT\include" `
    $CPP_OUT `
    -o `
    $BIN_OUT

Write-Host "Successfully compiled to binary output: $BIN_OUT"
Write-Host ""

Write-Host "✅ Compilation finished."