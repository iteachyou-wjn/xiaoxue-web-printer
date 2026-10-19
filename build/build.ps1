# ============================================================
#  晓雪WEB打印控件 - 一键打包脚本
#  用法: 双击 打包.bat (或直接运行本脚本)，按提示输入版本号
#  例如输入 1.0.4 -> 产出 build\windows-晓雪WEB打印控件-1.0.4-x86_64.exe
# ============================================================

param([string]$Version)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 项目根目录 (脚本在 build 下，根目录为其上一级)
$root = Split-Path $PSScriptRoot -Parent

# 工具路径
$mvn      = 'D:\Dev\apache-maven-3.9.9\bin\mvn.cmd'
$java     = 'D:\Program Files\Java\jdk-17\bin\java.exe'
$jpackage = 'D:\Program Files\Java\jdk-17\bin\jpackage.exe'
$iscc     = 'D:\Program Files (x86)\Inno Setup 6\ISCC.exe'

# ---------- 输入并校验版本号 ----------
if (-not $Version) {
    $Version = Read-Host '请输入版本号 (如 1.0.4)'
}
$Version = $Version.Trim()
if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    Write-Host "版本号格式错误: [$Version]，应为 如 1.0.4" -ForegroundColor Red
    exit 1
}
Write-Host ">>> 开始打包版本 $Version ..." -ForegroundColor Cyan

# ---------- 准备 ProGuard 运行时 (build/tools) ----------
$toolsDir = Join-Path $PSScriptRoot 'tools'
New-Item -ItemType Directory -Force $toolsDir | Out-Null
$pgBase   = Join-Path $toolsDir 'proguard-base-7.2.2.jar'
$pgCore   = Join-Path $toolsDir 'proguard-core-9.0.1.jar'
$pgLogApi = Join-Path $toolsDir 'log4j-api-2.20.0.jar'
$pgLogCor = Join-Path $toolsDir 'log4j-core-2.20.0.jar'

function Ensure-Tool([string]$Dest, [string]$Repo, [string]$Url) {
    if (-not (Test-Path $Dest)) {
        if ($Repo -and (Test-Path $Repo)) { Copy-Item $Repo $Dest; Write-Host "  准备工具: $(Split-Path $Dest -Leaf)" }
        elseif ($Url) { & curl.exe -sL -o $Dest $Url; Write-Host "  下载工具: $(Split-Path $Dest -Leaf)" }
    }
}
Ensure-Tool $pgBase 'D:\Dev\LocalRepository\com\guardsquare\proguard-base\7.2.2\proguard-base-7.2.2.jar' 'https://repo1.maven.org/maven2/com/guardsquare/proguard-base/7.2.2/proguard-base-7.2.2.jar'
Ensure-Tool $pgCore 'D:\Dev\LocalRepository\com\guardsquare\proguard-core\9.0.1\proguard-core-9.0.1.jar' 'https://repo1.maven.org/maven2/com/guardsquare/proguard-core/9.0.1/proguard-core-9.0.1.jar'
Ensure-Tool $pgLogApi $null 'https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.20.0/log4j-api-2.20.0.jar'
Ensure-Tool $pgLogCor $null 'https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.20.0/log4j-core-2.20.0.jar'

# ---------- 1/6 更新版本号 (pom / iss / proguard.pro) ----------
Write-Host "[1/6] 更新版本号到 $Version ..."
$pomPath = Join-Path $root 'pom.xml'
$pomText = [System.IO.File]::ReadAllText($pomPath)
$pomText = $pomText -replace '(?s)(<artifactId>dreamer-print-service</artifactId>\s*<version>)[^<]*(</version>)', "`${1}$Version`${2}"
[System.IO.File]::WriteAllText($pomPath, $pomText, (New-Object System.Text.UTF8Encoding($false)))

$issPath = Join-Path $PSScriptRoot 'dreamer-print.iss'
$issText = [System.IO.File]::ReadAllText($issPath)
$issText = $issText -replace '#define MyAppVersion "[^"]*"', "#define MyAppVersion `"$Version`""
[System.IO.File]::WriteAllText($issPath, $issText, (New-Object System.Text.UTF8Encoding($false)))

$utf8 = New-Object System.Text.UTF8Encoding($false)
$pgPath = Join-Path $PSScriptRoot 'proguard.pro'
$pgText = [System.IO.File]::ReadAllText($pgPath, $utf8)
$pgText = $pgText -replace 'dreamer-print-service-\d+\.\d+\.\d+', "dreamer-print-service-$Version"
[System.IO.File]::WriteAllText($pgPath, $pgText, $utf8)

# ---------- 2/6 Maven 构建 ----------
Write-Host "[2/6] Maven 构建 (clean package) ..."
Push-Location $root
& $mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'Maven 构建失败' }
Pop-Location

# ---------- 3/6 复制依赖 ----------
Write-Host "[3/6] 复制依赖到 build/libs ..."
$libsDir = Join-Path $PSScriptRoot 'libs'
Remove-Item -Recurse -Force $libsDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $libsDir | Out-Null
Push-Location $root
& $mvn dependency:copy-dependencies -DoutputDirectory=build\libs
if ($LASTEXITCODE -ne 0) { throw '复制依赖失败' }
Pop-Location

# ---------- 4/6 准备 input 并 ProGuard 混淆 ----------
Write-Host "[4/6] ProGuard 混淆瘦 jar ..."
$inputDir = Join-Path $PSScriptRoot 'input'
Remove-Item -Recurse -Force $inputDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $inputDir | Out-Null
$thinJar = Join-Path $inputDir "dreamer-print-service-$Version.jar"
Copy-Item (Join-Path $root "target\original-dreamer-print-service-$Version.jar") $thinJar
Copy-Item (Join-Path $libsDir '*.jar') $inputDir

# 按本次实际依赖重写 libraryjars，避免新增 jar（如 JasperReports）漏配导致混淆失败
$utf8 = New-Object System.Text.UTF8Encoding($false)
$libLines = New-Object System.Collections.Generic.List[string]
Get-ChildItem $libsDir -Filter *.jar | Sort-Object Name | ForEach-Object {
    if ($_.Name -like 'bartender-printer-sdk-*.jar') {
        $libLines.Add("-libraryjars libs/$($_.Name)(!cn/databytes/bartender/JbtCob.class)")
    } else {
        $libLines.Add("-libraryjars libs/$($_.Name)")
    }
}
if ($libLines.Count -eq 0) { throw 'build/libs 下没有依赖 jar' }
$pgText = [System.IO.File]::ReadAllText($pgPath, $utf8)
if ($pgText -notmatch '(?s)# BEGIN_LIBS\r?\n.*?# END_LIBS') {
    throw 'proguard.pro 中找不到 BEGIN_LIBS / END_LIBS 标记'
}
$libBlock = ($libLines -join "`r`n")
# Regex 替换串里 $ 有特殊含义，jar 名若含 $ 必须转成 $$
$replacement = ("# BEGIN_LIBS`r`n$libBlock`r`n# END_LIBS").Replace('$', '$$')
$updated = [regex]::Replace($pgText, '(?s)# BEGIN_LIBS\r?\n.*?# END_LIBS', $replacement)
[System.IO.File]::WriteAllText($pgPath, $updated, $utf8)
Write-Host "  已写入 $($libLines.Count) 个依赖 library jar"

& $java -cp "$pgBase;$pgCore;$pgLogApi;$pgLogCor" proguard.ProGuard "@$pgPath"
if ($LASTEXITCODE -ne 0) { throw 'ProGuard 混淆失败' }
$obfJar = Join-Path $inputDir "dreamer-print-service-$Version-obf.jar"
if (-not (Test-Path $obfJar)) { throw 'ProGuard 混淆失败' }
Remove-Item $thinJar
Rename-Item $obfJar "dreamer-print-service-$Version.jar"

# ---------- 5/6 jpackage ----------
Write-Host "[5/6] jpackage 生成 app-image ..."
$appImage = Join-Path $PSScriptRoot 'app-image'
Remove-Item -Recurse -Force $appImage -ErrorAction SilentlyContinue
& $jpackage --type app-image --name "晓雪WEB打印控件" --app-version $Version --vendor iteachyou `
    --input $inputDir --main-jar "dreamer-print-service-$Version.jar" `
    --main-class cc.iteachyou.printservice.MainApp `
    --icon (Join-Path $PSScriptRoot 'dreamer-print.ico') --dest $appImage
if ($LASTEXITCODE -ne 0) { throw 'jpackage 失败' }

# jpackage 布局：安装目录/晓雪WEB打印控件.exe，主 jar 在安装目录/app/。
# reports、fonts 放到安装目录（与 exe 同级），安装后从这里读取，不读 jar 内资源。
$installRoot = Join-Path $appImage '晓雪WEB打印控件'
if (-not (Test-Path $installRoot)) { throw 'jpackage 未生成安装目录' }

function Copy-InstallResourceDir([string]$Name, [string[]]$Extensions, [bool]$Required = $true) {
    $dest = Join-Path $installRoot $Name
    New-Item -ItemType Directory -Force $dest | Out-Null
    $src = Join-Path $root "src\main\resources\$Name"
    $files = @(Get-ChildItem $src -File -ErrorAction SilentlyContinue |
        Where-Object { $Extensions -contains $_.Extension.ToLower() })
    if ($files.Count -eq 0) {
        if ($Required) { throw "src/main/resources/$Name 下没有所需文件" }
        Write-Host "  已创建安装目录\$Name"
        return
    }
    Copy-Item $files.FullName $dest -Force
    Write-Host "  已复制 $($files.Count) 个文件到安装目录\$Name"
}

Write-Host "  复制 reports/jr、reports/bt、fonts 到安装目录 ..."
Copy-InstallResourceDir 'reports\jr' @('.jasper', '.jrxml') $true
Copy-InstallResourceDir 'reports\bt' @('.btw') $false
Copy-InstallResourceDir 'fonts' @('.ttf', '.ttc', '.otf') $true

# ---------- 6/6 Inno Setup ----------
Write-Host "[6/6] Inno Setup 编译安装包 ..."
& $iscc (Join-Path $PSScriptRoot 'dreamer-print.iss')
if ($LASTEXITCODE -ne 0) { throw 'Inno Setup 编译失败' }

# 仅保留当前版本安装包，删除 build 下其它 windows-*.exe
$keepExe = "windows-晓雪WEB打印控件-$Version-x86_64.exe"
Get-ChildItem $PSScriptRoot -Filter 'windows-*.exe' -File | ForEach-Object {
    if ($_.Name -ne $keepExe) {
        Remove-Item $_.FullName -Force
        Write-Host "  已删除旧安装包: $($_.Name)"
    }
}

# ---------- 完成 ----------
Write-Host ""
Write-Host "打包完成！" -ForegroundColor Green
Write-Host "安装包: build\windows-晓雪WEB打印控件-$Version-x86_64.exe" -ForegroundColor Green
Write-Host ""
Read-Host '按回车键退出'
