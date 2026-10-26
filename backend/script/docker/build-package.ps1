param(
    [Alias("Server")]
    [switch]$Backend,

    [Alias("Admin")]
    [switch]$Frontend,

    [Alias("Mall", "H5")]
    [switch]$Mobile,

    [Alias("Target", "Component")]
    [string]$Only,

    [switch]$SkipBuild,
    [string]$HBuilderXCliPath = $env:HBUILDERX_CLI_PATH
)

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$BackendRoot = (Resolve-Path (Join-Path $ScriptDir "../..")).Path
$ProjectRoot = (Resolve-Path (Join-Path $BackendRoot "..")).Path
$FrontendRoot = Join-Path $ProjectRoot "frontend/admin-vue3"
$MallRoot = Join-Path $ProjectRoot "frontend/mall-uniapp"
if (-not $HBuilderXCliPath) {
    $HBuilderXCliPath = $env:SHOPRO_H5_CLI
}

# 处理通过 -Only / -Target / -Component 指定的情况
if ($Only) {
    switch ($Only.ToLowerInvariant()) {
        { $_ -in "backend", "server" } { $Backend = $true }
        { $_ -in "frontend", "admin" } { $Frontend = $true }
        { $_ -in "mobile", "mall", "h5" } { $Mobile = $true }
        "all" { $Backend = $true; $Frontend = $true; $Mobile = $true }
        default {
            throw "未知的打包目标: $Only 。支持的目标: all, backend, frontend, mobile。 "
        }
    }
}

# 若未指定任何单独组件，则默认打包全部
if (-not $Backend -and -not $Frontend -and -not $Mobile) {
    $Backend = $true
    $Frontend = $true
    $Mobile = $true
}

$targetNames = @()
if ($Backend) { $targetNames += "后端 (qiji-server)" }
if ($Frontend) { $targetNames += "管理前端 (admin-vue3)" }
if ($Mobile) { $targetNames += "移动端 H5 (mall-uniapp)" }
Write-Host "==> 打包目标: $($targetNames -join ', ')"

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments
    )
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "命令执行失败 (退出码 $LASTEXITCODE): $Command $Arguments "
    }
}

function Resolve-HBuilderXCli {
    param(
        [string]$ConfiguredPath
    )

    if ($ConfiguredPath) {
        $configuredCommand = Get-Command $ConfiguredPath -CommandType Application -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($configuredCommand) {
            return $configuredCommand.Source
        }
        $resolved = Resolve-Path -LiteralPath $ConfiguredPath -ErrorAction SilentlyContinue
        if ($resolved) {
            return $resolved.Path
        }
        throw "HBuilderX CLI 路径不存在: $ConfiguredPath 。请通过 -HBuilderXCliPath 或 HBUILDERX_CLI_PATH 指定 cli.exe/cli.cmd。 "
    }

    # `cli` 在 Windows PowerShell 中可能是内置别名，因此只接受真正的可执行文件。
    $command = Get-Command cli.exe, cli.cmd -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($command) {
        return $command.Source
    }

    $runningHBuilder = Get-Process HBuilderX -ErrorAction SilentlyContinue |
        Where-Object { $_.Path } |
        Select-Object -First 1
    if ($runningHBuilder) {
        $candidateCli = Join-Path (Split-Path -Parent $runningHBuilder.Path) "cli.exe"
        if (Test-Path -LiteralPath $candidateCli -PathType Leaf) {
            return $candidateCli
        }
    }

    foreach ($candidate in @(
        "D:\soft\HBuilderX\cli.exe",
        "C:\Program Files\HBuilderX\cli.exe",
        "D:\Program Files\HBuilderX\cli.exe",
        "C:\HBuilderX\cli.exe",
        "D:\HBuilderX\cli.exe"
    )) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }

    throw "未找到 HBuilderX CLI。请将 cli.exe/cli.cmd 加入 PATH，或通过 -HBuilderXCliPath 指定路径。 "
}

function Resolve-MallH5Dist {
    if ($env:SHOPRO_H5_DIST) {
        return $env:SHOPRO_H5_DIST
    }
    $webDist = Join-Path $MallRoot "unpackage/dist/build/web"
    if (Test-Path -LiteralPath (Join-Path $webDist "index.html") -PathType Leaf) {
        return $webDist
    }
    return (Join-Path $MallRoot "unpackage/dist/build/h5")
}

# 1. 构建后端
if ($Backend) {
    if ($SkipBuild) {
        Write-Host "[1/3] 跳过后端构建，使用已有制品 "
    } else {
        Write-Host "[1/3] 构建后端 JAR..."
        Invoke-Checked mvn -f (Join-Path $BackendRoot "pom.xml") clean package -pl qiji-server -am -DskipTests
    }
}

# 2. 构建管理前端
if ($Frontend) {
    if ($SkipBuild) {
        Write-Host "[2/3] 跳过管理前端构建，使用已有制品 "
    } else {
        Write-Host "[2/3] 构建管理前端..."
        Push-Location $FrontendRoot
        try {
            Invoke-Checked pnpm install --frozen-lockfile
            Invoke-Checked pnpm build:docker
        } finally {
            Pop-Location
        }
    }
}

# 3. 构建移动端 H5
if ($Mobile) {
    if ($SkipBuild) {
        Write-Host "[3/3] 跳过移动端 H5 构建，使用已有制品 "
    } elseif ($env:SHOPRO_H5_DIST) {
        Write-Host "[3/3] 使用 SHOPRO_H5_DIST 指定的 mall-uniapp H5 制品 "
    } else {
        Write-Host "[3/3] 构建 mall-uniapp H5..."
        $MallCli = Resolve-HBuilderXCli -ConfiguredPath $HBuilderXCliPath
        Invoke-Checked $MallCli publish web --project $MallRoot --webHosting false --ssr false
    }
}

# 4. 归集制品文件与配置
Write-Host "[归集] 校验并归集目标制品..."
$DeployBackend = Join-Path $ScriptDir "backend"
$DeployFrontendDist = Join-Path $ScriptDir "frontend/dist"
$DeployMallH5Dist = Join-Path $ScriptDir "frontend/mall-h5"
$DeployMysqlInit = Join-Path $ScriptDir "mysql/init"

if ($Backend) {
    $BackendJar = Join-Path $BackendRoot "qiji-server/target/qiji-server.jar"
    if (-not (Test-Path -LiteralPath $BackendJar -PathType Leaf)) {
        throw "未找到后端构建产物: $BackendJar "
    }
    New-Item -ItemType Directory -Force -Path $DeployBackend, $DeployMysqlInit | Out-Null
    Copy-Item -LiteralPath $BackendJar -Destination (Join-Path $DeployBackend "qiji-server.jar") -Force
    Copy-Item -LiteralPath (Join-Path $BackendRoot "sql/mysql/ruoyi-vue-pro.sql") -Destination (Join-Path $DeployMysqlInit "00-ruoyi-vue-pro.sql") -Force
    Copy-Item -LiteralPath (Join-Path $BackendRoot "sql/mysql/quartz.sql") -Destination (Join-Path $DeployMysqlInit "05-quartz.sql") -Force
    Copy-Item -LiteralPath (Join-Path $BackendRoot "sql/module/cps-all-in-one.sql") -Destination (Join-Path $DeployMysqlInit "10-cps-all-in-one.sql") -Force
    $AiSeed = Join-Path $BackendRoot "sql/module/ai-all.sql"
    if (Test-Path -LiteralPath $AiSeed -PathType Leaf) {
        Copy-Item -LiteralPath $AiSeed -Destination (Join-Path $DeployMysqlInit "15-ai-all.sql") -Force
    } else {
        Write-Warning "ai-all.sql is absent in upstream; core CPS can be packaged, but AI default roles/data are NOT initialized."
    }
    Write-Host "  [OK] 后端 JAR 与数据库初始化脚本已归集 "
}

if ($Frontend) {
    $FrontendDist = Join-Path $FrontendRoot "dist-prod"
    if (-not (Test-Path -LiteralPath (Join-Path $FrontendDist "index.html") -PathType Leaf)) {
        throw "未找到前端构建产物: $FrontendDist/index.html "
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $DeployFrontendDist) | Out-Null
    if (Test-Path -LiteralPath $DeployFrontendDist) {
        Remove-Item -LiteralPath $DeployFrontendDist -Recurse -Force
    }
    Copy-Item -LiteralPath $FrontendDist -Destination $DeployFrontendDist -Recurse
    Write-Host "  [OK] 管理前端产物已归集: $DeployFrontendDist "
}

if ($Mobile) {
    $MallH5Dist = Resolve-MallH5Dist
    if (-not (Test-Path -LiteralPath (Join-Path $MallH5Dist "index.html") -PathType Leaf)) {
        throw "未找到 mall-uniapp H5 构建产物: $MallH5Dist/index.html 。请使用 HBuilderX 发行 H5，或设置 SHOPRO_H5_DIST 指向 H5 dist 目录。 "
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $DeployMallH5Dist) | Out-Null
    if (Test-Path -LiteralPath $DeployMallH5Dist) {
        Remove-Item -LiteralPath $DeployMallH5Dist -Recurse -Force
    }
    Copy-Item -LiteralPath $MallH5Dist -Destination $DeployMallH5Dist -Recurse
    Write-Host "  [OK] 移动端 H5 产物已归集: $DeployMallH5Dist "
}

# 5. 校验部署目录
if (Get-Command docker -ErrorAction SilentlyContinue) {
    & docker compose version *> $null
    if ($LASTEXITCODE -eq 0) {
        Push-Location $ScriptDir
        try {
            Invoke-Checked docker compose --env-file docker.env config --quiet
        } finally {
            Pop-Location
        }
    } else {
        Write-Host "未检测到 Docker Compose，跳过 Compose 配置校验。 "
    }
} else {
    Write-Host "未检测到 Docker Compose，跳过 Compose 配置校验。 "
}

Write-Host ""
Write-Host "部署包准备完成: $ScriptDir "
if ($Backend) { Write-Host "  - 后端 JAR: $(Join-Path $DeployBackend 'qiji-server.jar')" }
if ($Frontend) { Write-Host "  - 管理前端: $DeployFrontendDist" }
if ($Mobile) { Write-Host "  - 移动端 H5: $DeployMallH5Dist" }
Write-Host "复制对应文件到服务器后，执行: bash deploy.sh redeploy"
