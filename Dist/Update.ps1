#Requires -Version 5.1
<#
    LCD 次像素最小复现 —— 下载最新构建包到 Dist 目录（实际逻辑）
    一般不用直接运行本文件：双击同目录的 Update.cmd 即可。

    参数：
      -Repo     覆盖仓库，默认 acnxo/LCDSubpixelMinimalReproduction
      -Match    包名关键字（子串匹配），例如 jdk23 / 25.0.4 / jdk25-openjfx25.0.4
      -Stable   只认正式版（默认包含预发行版）
#>
[CmdletBinding()]
param(
    [string]$Repo = 'acnxo/LCDSubpixelMinimalReproduction',
    [string]$Match = '',
    [switch]$Stable
)

$ErrorActionPreference = 'Stop'
$DistDir = $PSScriptRoot

# GitHub API 要求带 User-Agent；公开仓库不需要令牌
$headers = @{
    'User-Agent' = 'lcd-subpixel-repro-updater'
    'Accept'     = 'application/vnd.github+json'
}

Write-Host "仓库  ：$Repo"
Write-Host "目标  ：$DistDir"
if ($Match -ne '') { Write-Host "关键字：$Match" }
Write-Host ''

# 不走 /releases/latest —— 那个接口只认「非预发行」，本仓库的包全是预发行，会 404
$api = "https://api.github.com/repos/$Repo/releases?per_page=30"
try {
    $releases = @(Invoke-RestMethod -Uri $api -Headers $headers)
} catch {
    Write-Host "[错误] 查询发布页失败：$($_.Exception.Message)" -ForegroundColor Red
    Write-Host '       请检查网络（公司代理/防火墙）后重试。'
    exit 1
}

if ($releases.Count -eq 0) {
    Write-Host '[提示] 该仓库还没有任何发布。' -ForegroundColor Yellow
    Write-Host '       请先在 GitHub 的 Actions 页面手动跑一次构建（Run workflow），'
    Write-Host '       等它把构建包发布出来，再回来双击本脚本。'
    exit 1
}

# ---------- 第 1 步：挑一个 release + 一个包 ----------
$pick = $null
foreach ($r in $releases) {
    if ($Stable -and $r.prerelease) { continue }
    $assets = @($r.assets | Where-Object { $_.name -like '*.zip' })
    if ($assets.Count -eq 0) { continue }

    if ($Match -ne '') {
        $hit = @($assets | Where-Object { $_.name -like "*$Match*" })
        if ($hit.Count -gt 0) { $pick = @{ Release = $r; Asset = $hit[0] }; break }
        continue
    }

    # 未给关键字：把这一版的包列出来让用户选（每个组合一格）
    Write-Host ("找到发布：{0}  （{1}，{2} 个包）" -f $r.name, $(if ($r.prerelease) { '预发行' } else { '正式版' }), $assets.Count)
    Write-Host ''
    for ($i = 0; $i -lt $assets.Count; $i++) {
        Write-Host ("  [{0}] {1}" -f ($i + 1), $assets[$i].name)
    }
    Write-Host ''
    $sel = Read-Host "请输入要下载的编号（直接回车 = 1）"
    if ([string]::IsNullOrWhiteSpace($sel)) { $sel = '1' }
    $idx = 0
    if (-not [int]::TryParse($sel, [ref]$idx)) { $idx = 0 }
    if ($idx -lt 1 -or $idx -gt $assets.Count) {
        Write-Host '[错误] 编号超出范围。' -ForegroundColor Red
        exit 1
    }
    $pick = @{ Release = $r; Asset = $assets[$idx - 1] }
    break
}

if ($null -eq $pick) {
    if ($Match -ne '') {
        Write-Host "[提示] 在最近的发布里没找到名字含「$Match」的包。" -ForegroundColor Yellow
        Write-Host '       可用关键字示例：jdk17 / jdk20 / jdk21 / jdk22 / jdk23 / jdk24 / jdk25 / 27'
    } else {
        Write-Host '[提示] 没有找到带 zip 包的发布。' -ForegroundColor Yellow
    }
    exit 1
}

Write-Host ''
Write-Host ("选中：{0}" -f $pick.Asset.name)
Write-Host ("来自：{0}" -f $pick.Release.name)

# ---------- 第 2 步：下载 + 解压 ----------
$tmp = Join-Path $env:TEMP ('lcd-repro-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$zip = Join-Path $tmp 'pkg.zip'
$ext = Join-Path $tmp 'x'

try {
    Write-Host '下载中……'
    Invoke-WebRequest -Uri $pick.Asset.browser_download_url -Headers $headers -OutFile $zip
    Expand-Archive -LiteralPath $zip -DestinationPath $ext -Force

    # ---------- 第 3 步：覆盖到 Dist ----------
    Copy-Item -Path (Join-Path $ext '*') -Destination $DistDir -Recurse -Force
    Write-Host '已更新本目录。' -ForegroundColor Green
    Write-Host ''
    Write-Host '本目录现有文件：'
    Get-ChildItem -Path $DistDir -File | ForEach-Object { Write-Host ("  {0}" -f $_.Name) }

    $result = Join-Path $DistDir 'RESULT.txt'
    if (Test-Path $result) {
        Write-Host ''
        Write-Host '===== RESULT.txt（构建机上的判据数字）====='
        Get-Content -LiteralPath $result | ForEach-Object { Write-Host "  $_" }
        Write-Host '=========================================='
    }
} finally {
    Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
}
