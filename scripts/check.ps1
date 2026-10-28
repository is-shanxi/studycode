#requires -Version 5.1
[CmdletBinding()]
param(
    [switch]$IncludeE2E,
    [switch]$SkipFrontend,
    [switch]$SkipJava,
    [switch]$SkipPython
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$script:Failures = [System.Collections.Generic.List[string]]::new()

function Write-Step {
    param([Parameter(Mandatory)][string]$Name)

    Write-Host ""
    Write-Host "==> $Name" -ForegroundColor Cyan
}

function Invoke-External {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$FilePath,
        [string[]]$Arguments = @()
    )

    Write-Step -Name $Name
    & $FilePath @Arguments

    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILED: $Name (exit $LASTEXITCODE)" -ForegroundColor Red
        $script:Failures.Add($Name)
    }
}

function Resolve-FrontendPackageManager {
    $pnpm = Get-Command pnpm -ErrorAction SilentlyContinue
    if ($null -ne $pnpm) {
        try {
            & pnpm --version *> $null
            if ($LASTEXITCODE -eq 0) {
                return @{ FilePath = 'pnpm'; Prefix = @() }
            }
        }
        catch {
            # Fall through to the npx-based fallback below.
        }
    }

    Write-Warning 'pnpm 不可用，改用 npx pnpm@10。请修复 corepack 或安装 pnpm 10。'
    return @{ FilePath = 'npx'; Prefix = @('--yes', 'pnpm@10') }
}

if (-not $SkipFrontend) {
    $packageManager = Resolve-FrontendPackageManager
    $frontendDir = Join-Path $RepoRoot 'frontend'
    Push-Location $frontendDir
    try {
        Invoke-External -Name 'frontend lint' -FilePath $packageManager.FilePath `
            -Arguments (@($packageManager.Prefix) + @('lint'))
        Invoke-External -Name 'frontend format check' -FilePath $packageManager.FilePath `
            -Arguments (@($packageManager.Prefix) + @('format:check'))
        Invoke-External -Name 'frontend typecheck' -FilePath $packageManager.FilePath `
            -Arguments (@($packageManager.Prefix) + @('typecheck'))
        Invoke-External -Name 'frontend unit tests' -FilePath $packageManager.FilePath `
            -Arguments (@($packageManager.Prefix) + @('test:unit'))

        if ($IncludeE2E) {
            Invoke-External -Name 'frontend e2e tests' -FilePath $packageManager.FilePath `
                -Arguments (@($packageManager.Prefix) + @('test:e2e'))
        }
    }
    finally {
        Pop-Location
    }
}

if (-not $SkipJava) {
    $javaDir = Join-Path $RepoRoot 'backend/java'
    Push-Location $javaDir
    try {
        $mavenWrapperCmd = Join-Path $javaDir 'mvnw.cmd'
        $mavenWrapper = Join-Path $javaDir 'mvnw'
        $maven = $null

        if (Test-Path $mavenWrapperCmd) {
            $maven = $mavenWrapperCmd
        }
        elseif (Test-Path $mavenWrapper) {
            $maven = $mavenWrapper
        }
        elseif ($null -ne (Get-Command mvn -ErrorAction SilentlyContinue)) {
            $maven = 'mvn'
        }
        else {
            Write-Host 'FAILED: Maven wrapper and mvn are both unavailable.' -ForegroundColor Red
            $script:Failures.Add('java setup')
        }

        if ($null -ne $maven) {
            Invoke-External -Name 'java verify' -FilePath $maven -Arguments @('verify')
        }
    }
    finally {
        Pop-Location
    }
}

if (-not $SkipPython) {
    $pythonDir = Join-Path $RepoRoot 'backend/python'
    Push-Location $pythonDir
    try {
        $venvPython = Join-Path $pythonDir '.venv/Scripts/python.exe'
        if (-not (Test-Path $venvPython)) {
            $venvPython = Join-Path $pythonDir '.venv/bin/python'
        }

        if (-not (Test-Path $venvPython)) {
            $systemPython = Get-Command python -ErrorAction SilentlyContinue
            if ($null -eq $systemPython) {
                Write-Host 'FAILED: python is unavailable.' -ForegroundColor Red
                $script:Failures.Add('python setup')
                $venvPython = $null
            }
            else {
                $venvPython = $systemPython.Source
                Write-Warning 'backend/python/.venv 不存在，使用系统 Python。建议先创建虚拟环境。'
            }
        }

        if ($null -ne $venvPython) {
            Invoke-External -Name 'python lint' -FilePath $venvPython `
                -Arguments @('-m', 'ruff', 'check', '.')
            Invoke-External -Name 'python format check' -FilePath $venvPython `
                -Arguments @('-m', 'ruff', 'format', '--check', '.')
            Invoke-External -Name 'python typecheck' -FilePath $venvPython `
                -Arguments @('-m', 'mypy', 'app', 'tests')
            Invoke-External -Name 'python tests' -FilePath $venvPython `
                -Arguments @('-m', 'pytest')
        }
    }
    finally {
        Pop-Location
    }
}

if ($script:Failures.Count -gt 0) {
    Write-Host ""
    Write-Host "校验失败（$($script:Failures.Count) 项）：" -ForegroundColor Red
    foreach ($failure in $script:Failures) {
        Write-Host " - $failure" -ForegroundColor Red
    }
    exit 1
}

Write-Host ""
Write-Host '全部检查通过。' -ForegroundColor Green
