<#
.SYNOPSIS
Starts PostgreSQL, the Java API, the Python AI service and the Vite frontend.

.DESCRIPTION
Builds the Java jar when needed, prepares missing Python/frontend dependencies,
starts every service in the background, writes PID files and logs under .run/,
and waits for each health endpoint before returning.

.PARAMETER SkipBuild
Fail instead of rebuilding the Java jar when it is missing or stale.

.PARAMETER NoPython
Do not start the Python AI service.

.PARAMETER NoFrontend
Do not start the Vite frontend.

.EXAMPLE
./scripts/start-dev.ps1

.EXAMPLE
./scripts/start-dev.ps1 -SkipBuild
#>
#requires -Version 5.1
[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$NoPython,
    [switch]$NoFrontend
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $RepoRoot '.run'
$LogDir = Join-Path $RunDir 'logs'
$JavaDir = Join-Path $RepoRoot 'backend/java'
$PythonDir = Join-Path $RepoRoot 'backend/python'
$FrontendDir = Join-Path $RepoRoot 'frontend'
$EnvFile = Join-Path $RepoRoot 'infra/env/.env.local'
$EnvExample = Join-Path $RepoRoot 'infra/env/.env.example'
$ComposeFile = Join-Path $RepoRoot 'infra/compose.yaml'
$script:ReusedServices = [System.Collections.Generic.List[string]]::new()

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

function Assert-Command {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Hint
    )
    if ($null -eq (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "缺少命令 '$Name'。$Hint"
    }
}

function Import-LocalEnvironment {
    param([Parameter(Mandatory)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
            continue
        }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) {
            continue
        }
        $key = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1)
        if ($key -match '^[A-Za-z_][A-Za-z0-9_]*$' -and -not (Test-Path "Env:$key")) {
            [Environment]::SetEnvironmentVariable($key, $value, 'Process')
        }
    }
}

function Test-HttpHealth {
    param([Parameter(Mandatory)][string]$Url)
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 400
    }
    catch {
        return $false
    }
}

function Test-PortListening {
    param([Parameter(Mandatory)][int]$Port)
    return $null -ne (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
}

function Get-ManagedProcess {
    param([Parameter(Mandatory)][string]$Name)
    $pidFile = Join-Path $RunDir "$Name.pid"
    if (-not (Test-Path -LiteralPath $pidFile)) {
        return $null
    }
    try {
        $info = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
        $process = Get-Process -Id $info.pid -ErrorAction SilentlyContinue
        if ($null -eq $process) {
            Remove-Item -LiteralPath $pidFile -Force
            return $null
        }
        return $info
    }
    catch {
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
        return $null
    }
}

function Wait-ForHealth {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Url,
        [int]$TimeoutSeconds = 90
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-HttpHealth -Url $Url) {
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "$Name 在 $TimeoutSeconds 秒内未通过健康检查：$Url"
}

function Start-ManagedProcess {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$FilePath,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$WorkingDirectory,
        [Parameter(Mandatory)][int]$Port,
        [Parameter(Mandatory)][string]$HealthUrl
    )

    $existing = Get-ManagedProcess -Name $Name
    if ($null -ne $existing) {
        if (Test-HttpHealth -Url $HealthUrl) {
            Write-Ok "$Name 已由本脚本启动，跳过"
            $script:ReusedServices.Add($Name)
            return
        }
        throw "$Name 的 PID 文件存在（PID $($existing.pid)）但健康检查失败；请先运行 ./scripts/stop-dev.ps1"
    }

    if (Test-PortListening -Port $Port) {
        if (Test-HttpHealth -Url $HealthUrl) {
            Write-Warn "$Name 端口已被外部进程占用且健康，跳过启动（stop-dev.ps1 不会停止它）"
            $script:ReusedServices.Add("$Name (external)")
            return
        }
        throw "$Name 端口已被其他进程占用且健康检查失败，请先排查端口占用"
    }

    $outLog = Join-Path $LogDir "$Name.out.log"
    $errLog = Join-Path $LogDir "$Name.err.log"
    $process = Start-Process -FilePath $FilePath -ArgumentList $Arguments `
        -WorkingDirectory $WorkingDirectory -WindowStyle Hidden `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru

    $info = [ordered]@{
        name = $Name
        pid = $process.Id
        processName = $process.ProcessName
        startedAt = (Get-Date).ToString('o')
        healthUrl = $HealthUrl
        stdout = $outLog
        stderr = $errLog
    }
    $info | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $RunDir "$Name.pid") -Encoding UTF8

    try {
        Wait-ForHealth -Name $Name -Url $HealthUrl
    }
    catch {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath (Join-Path $RunDir "$Name.pid") -Force -ErrorAction SilentlyContinue
        throw
    }
    Write-Ok "$Name 已启动（PID $($process.Id)）"
}

function Resolve-FrontendPackageManager {
    $pnpm = Get-Command pnpm -ErrorAction SilentlyContinue
    if ($null -ne $pnpm) {
        & pnpm --version *> $null
        if ($LASTEXITCODE -eq 0) {
            return @{ FilePath = 'pnpm'; Prefix = @() }
        }
    }
    return @{ FilePath = 'npx'; Prefix = @('--yes', 'pnpm@10') }
}

function Ensure-JavaJar {
    $targetDir = Join-Path $JavaDir 'target'
    $jar = $null
    if (Test-Path -LiteralPath $targetDir) {
        $jar = Get-ChildItem -LiteralPath $targetDir -Filter '*.jar' -File |
            Where-Object { $_.Name -notlike '*.original' } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
    }

    $stale = $null -eq $jar
    if (-not $stale) {
        $stale = $null -ne (Get-ChildItem -LiteralPath (Join-Path $JavaDir 'src') -Recurse -File |
            Where-Object { $_.LastWriteTime -gt $jar.LastWriteTime } |
            Select-Object -First 1)
    }

    if (-not $stale) {
        Write-Ok "Java jar 已是最新：$($jar.FullName)"
        return $jar.FullName
    }
    if ($SkipBuild) {
        throw "Java jar 缺失或已过期，但指定了 -SkipBuild"
    }

    Write-Step '构建 Java jar'
    $maven = Join-Path $JavaDir 'mvnw.cmd'
    if (-not (Test-Path -LiteralPath $maven)) {
        $maven = 'mvn'
        Assert-Command -Name $maven -Hint '请安装 Maven 或恢复 Maven Wrapper。'
    }
    Push-Location $JavaDir
    try {
        & $maven -q -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            throw "Java 构建失败（exit $LASTEXITCODE）"
        }
    }
    finally {
        Pop-Location
    }
    $jar = Get-ChildItem -LiteralPath $targetDir -Filter '*.jar' -File |
        Where-Object { $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $jar) {
        throw 'Java 构建完成但未找到 jar'
    }
    Write-Ok "Java jar 已构建：$($jar.FullName)"
    return $jar.FullName
}

function Ensure-PythonEnvironment {
    $venvPython = Join-Path $PythonDir '.venv/Scripts/python.exe'
    if (-not (Test-Path -LiteralPath $venvPython)) {
        $basePython = Get-Command python -ErrorAction SilentlyContinue
        if ($null -eq $basePython) {
            $basePython = Get-Command py -ErrorAction SilentlyContinue
        }
        if ($null -eq $basePython) {
            throw '缺少 python 命令，无法创建 backend/python/.venv'
        }
        Write-Step '创建 Python 虚拟环境'
        Push-Location $PythonDir
        try {
            if ($basePython.Name -eq 'py.exe') {
                & $basePython.Source -3 -m venv .venv
            }
            else {
                & $basePython.Source -m venv .venv
            }
            if ($LASTEXITCODE -ne 0) {
                throw "Python venv 创建失败（exit $LASTEXITCODE）"
            }
            & $venvPython -m pip install -e ".[dev]"
            if ($LASTEXITCODE -ne 0) {
                throw "Python 依赖安装失败（exit $LASTEXITCODE）"
            }
        }
        finally {
            Pop-Location
        }
    }
    else {
        & $venvPython -c 'import fastapi, uvicorn, pytest' *> $null
        if ($LASTEXITCODE -ne 0) {
            Write-Step '补装 Python 依赖'
            Push-Location $PythonDir
            try {
                & $venvPython -m pip install -e ".[dev]"
                if ($LASTEXITCODE -ne 0) {
                    throw "Python 依赖安装失败（exit $LASTEXITCODE）"
                }
            }
            finally {
                Pop-Location
            }
        }
    }
    return $venvPython
}

function Ensure-FrontendDependencies {
    $viteEntry = Join-Path $FrontendDir 'node_modules/vite/bin/vite.js'
    if (Test-Path -LiteralPath $viteEntry) {
        return $viteEntry
    }
    Write-Step '安装前端依赖'
    $packageManager = Resolve-FrontendPackageManager
    $installArgs = @($packageManager.Prefix) + @('install')
    Push-Location $FrontendDir
    try {
        & $packageManager.FilePath @installArgs
        if ($LASTEXITCODE -ne 0) {
            throw "前端依赖安装失败（exit $LASTEXITCODE）"
        }
    }
    finally {
        Pop-Location
    }
    if (-not (Test-Path -LiteralPath $viteEntry)) {
        throw '前端依赖安装后仍未找到 Vite 入口'
    }
    return $viteEntry
}

# ---------------------------------------------------------------------------
# Preconditions
# ---------------------------------------------------------------------------
Assert-Command -Name docker -Hint '请先安装并启动 Docker Desktop。'
Assert-Command -Name java -Hint '请安装 Java 21 并加入 PATH。'

if (-not (Test-Path -LiteralPath $EnvFile)) {
    Copy-Item -LiteralPath $EnvExample -Destination $EnvFile
    Write-Ok "已从模板创建 $EnvFile"
}
Import-LocalEnvironment -Path $EnvFile
New-Item -ItemType Directory -Path $RunDir -Force | Out-Null
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null

# ---------------------------------------------------------------------------
# PostgreSQL
# ---------------------------------------------------------------------------
Write-Step '启动 PostgreSQL'
& docker compose --env-file $EnvFile -f $ComposeFile up -d postgres
if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL 启动失败（exit $LASTEXITCODE）"
}
$postgresReady = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    $health = & docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' cert-practice-postgres 2>$null
    if ($health -eq 'healthy') {
        $postgresReady = $true
        break
    }
    Start-Sleep -Seconds 2
}
if (-not $postgresReady) {
    throw 'PostgreSQL 容器未在 120 秒内进入 healthy 状态'
}
Write-Ok 'PostgreSQL 已就绪'

# ---------------------------------------------------------------------------
# Java
# ---------------------------------------------------------------------------
Write-Step '启动 Java API'
$jarPath = Ensure-JavaJar
Start-ManagedProcess -Name 'java' -FilePath 'java' -Arguments @('-jar', $jarPath) `
    -WorkingDirectory $JavaDir -Port 8080 -HealthUrl 'http://localhost:8080/api/v1/health'

# ---------------------------------------------------------------------------
# Python
# ---------------------------------------------------------------------------
if (-not $NoPython) {
    Write-Step '启动 Python AI 服务'
    $venvPython = Ensure-PythonEnvironment
    Start-ManagedProcess -Name 'python' -FilePath $venvPython `
        -Arguments @('-m', 'uvicorn', 'app.main:app', '--host', '127.0.0.1', '--port', '8000') `
        -WorkingDirectory $PythonDir -Port 8000 -HealthUrl 'http://localhost:8000/health/ready'
}

# ---------------------------------------------------------------------------
# Frontend
# ---------------------------------------------------------------------------
if (-not $NoFrontend) {
    Write-Step '启动 Vite 前端'
    Assert-Command -Name node -Hint '请安装 Node.js 22 并加入 PATH。'
    $viteEntry = Ensure-FrontendDependencies
    Start-ManagedProcess -Name 'frontend' -FilePath 'node' `
        -Arguments @($viteEntry, '--port', '5173', '--strictPort') `
        -WorkingDirectory $FrontendDir -Port 5173 -HealthUrl 'http://localhost:5173/'
}

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host '项目已启动。' -ForegroundColor Green
Write-Host "  用户端     http://localhost:5173/app/login"
Write-Host "  管理端     http://localhost:5173/admin"
Write-Host "  Java API   http://localhost:8080/api/v1"
if (-not $NoPython) {
    Write-Host "  Python AI  http://localhost:8000"
}
Write-Host "  PostgreSQL localhost:5432"
Write-Host ""
Write-Host '演示账号：student01 / Student@12345，admin / Admin@12345'
Write-Host "日志目录：$LogDir"
Write-Host '查看日志：Get-Content .run/logs/java.out.log -Wait'
Write-Host '停止服务：./scripts/stop-dev.ps1（加 -IncludePostgres 同时停止数据库）'
if ($script:ReusedServices.Count -gt 0) {
    Write-Host ""
    Write-Warn "以下服务未由本脚本启动，stop-dev.ps1 不会停止它们：$($script:ReusedServices -join ', ')"
}
