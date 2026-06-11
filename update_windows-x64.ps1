Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

trap {
    [Console]::Error.WriteLine("Error: command failed: $($_.InvocationInfo.Line.Trim())")
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
}

$ROOT = Split-Path -Parent $MyInvocation.MyCommand.Path

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

$script:STEP_NO = 0

function Step {
    param(
        [Parameter(Mandatory = $true)]
        [string] $CommandText,

        [Parameter(Mandatory = $true)]
        [scriptblock] $Action
    )

    $script:STEP_NO++

    Write-Host ""
    Write-Host "[Step $script:STEP_NO] $CommandText"
    Read-Host "Press Enter to run, or Ctrl+C to abort"

    & $Action

    Write-Host "[Step $script:STEP_NO] Done."
}

New-Item -ItemType Directory -Force -Path $GEN_DIR, $CLS_DIR | Out-Null

Push-Location $ROOT

try {
    Step "Clear generated and class output directories" {
        Get-ChildItem -LiteralPath $GEN_DIR -Force -ErrorAction SilentlyContinue |
            Remove-Item -Recurse -Force

        Get-ChildItem -LiteralPath $CLS_DIR -Force -ErrorAction SilentlyContinue |
            Remove-Item -Recurse -Force
    }

    Step "Generate ANTLR parser/visitor files" {
        $antlrArgs = @(
            "-Xmx500M",
            "-cp", $ANTLR_CP,
            "org.antlr.v4.Tool",
            "-visitor",
            "-o", $GEN_DIR,
            "Franko.g4"
        )

        RunNative -Exe "java" -Argv $antlrArgs
    }

    Step "Compile Java compiler classes" {
        $javaFiles = @(
            Get-ChildItem -LiteralPath $ROOT -Filter "*.java" -File
            Get-ChildItem -LiteralPath $GEN_DIR -Filter "*.java" -File
        )

        if ($javaFiles.Count -eq 0) {
            throw "No Java source files found to compile."
        }

        $javacArgs = @(
            "--release", "25",
            "-Xlint:all,-auxiliaryclass",
            "-cp", "$ANTLR_CP;$GEN_DIR",
            "-d", $CLS_DIR
        )

        foreach ($file in $javaFiles) {
            $javacArgs += $file.FullName
        }

        RunNative -Exe "javac" -Argv $javacArgs
    }

    Write-Host ""
    Write-Host "✅ Compiler toolchain updated successfully."
}
finally {
    Pop-Location
}