<#
.SYNOPSIS
Stops the services started by scripts/start-dev.ps1.

.DESCRIPTION
Reads PID files under .run/, verifies each process still matches the recorded
name, stops it, and removes the PID file. PostgreSQL is left running unless
-IncludePostgres is specified. Logs under .run/logs/ are preserved.

.PARAMETER IncludePostgres
Also stop the PostgreSQL container.

.EXAMPLE
./scripts/stop-dev.ps1

.EXAMPLE
./scripts/stop-dev.ps1 -IncludePostgres
#>
#requires -Version 5.1
[CmdletBinding()]
param(
    [switch]$IncludePostgres
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $RepoRoot '.run'
$EnvFile = Join-Path $RepoRoot 'infra/env/.env.local'
$ComposeFile = Join-Path $RepoRoot 'infra/compose.yaml'
$script:Failures = [System.Collections.Generic.List[string]]::new()

function Write-Step {
    param([Parameter(Mandatory)][string]$Name)
    Write-Host ""
    Write-Host "==> $Name" -ForegroundColor Cyan
}

function Write-Ok {
    param([Parameter(Mandatory)][string]$Message)
    Write-Host "    OK  $Message" -ForegroundColor Green
}

function Write-Warn {
    param([Parameter(Mandatory)][string]$Message)
    Write-Host "    !!  $Message" -ForegroundColor Yellow
}

if (-not (Test-Path -LiteralPath $RunDir)) {
    Write-Host '没有找到 .run 目录，服务可能未由 start-dev.ps1 启动。' -ForegroundColor Yellow
}
else {
    # Stop frontend first, then backend processes.
    foreach ($name in @('frontend', 'python', 'java')) {
        $pidFile = Join-Path $RunDir "$name.pid"
        if (-not (Test-Path -LiteralPath $pidFile)) {
            Write-Step "停止 $name"
            Write-Host '    未找到 PID 文件，跳过（可能不是由 start-dev.ps1 启动）。'
            continue
        }

        Write-Step "停止 $name"
        try {
            $info = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
            $process = Get-Process -Id $info.pid -ErrorAction SilentlyContinue
            if ($null -eq $process) {
                Write-Ok "进程已不存在（PID $($info.pid)）"
                Remove-Item -LiteralPath $pidFile -Force
                continue
            }
            if ($process.ProcessName -ne $info.processName) {
                Write-Warn "PID $($info.pid) 已被其他进程复用（$($process.ProcessName)），不停止；已移除过期 PID 文件。"
                Remove-Item -LiteralPath $pidFile -Force
                continue
            }

            # Uvicorn and some wrappers spawn a child process, so stop the tree.
            & taskkill /PID $info.pid /T /F *> $null
            if ($LASTEXITCODE -ne 0) {
                throw "taskkill 失败（exit $LASTEXITCODE）"
            }
            Remove-Item -LiteralPath $pidFile -Force
            Write-Ok "已停止（PID $($info.pid) 及其子进程）"
        }
        catch {
            Write-Warn "停止失败：$($_.Exception.Message)"
            $script:Failures.Add($name)
        }
    }
}

if ($IncludePostgres) {
    Write-Step '停止 PostgreSQL'
    & docker compose --env-file $EnvFile -f $ComposeFile stop postgres
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "PostgreSQL 停止失败（exit $LASTEXITCODE）"
        $script:Failures.Add('postgres')
    }
    else {
        Write-Ok 'PostgreSQL 已停止（数据卷保留）'
    }
}
else {
    Write-Host ''
    Write-Host 'PostgreSQL 保持运行；如需停止请加 -IncludePostgres。' -ForegroundColor Yellow
}

Write-Host ''
if ($script:Failures.Count -gt 0) {
    Write-Host "停止过程中有 $($script:Failures.Count) 项失败：$($script:Failures -join ', ')" -ForegroundColor Red
    exit 1
}
Write-Host '服务已停止。日志保留在 .run/logs/。' -ForegroundColor Green
