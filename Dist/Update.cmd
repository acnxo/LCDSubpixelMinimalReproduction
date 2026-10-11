@echo off
rem ==========================================================================
rem  LCD 次像素最小复现 —— 「下载最新构建包」脚本（Update）
rem
rem  本脚本与本目录（Dist）一起用：双击它，就会从 GitHub 发布页面取最新的
rem  构建包（zip），下载并解压到本目录，也就是 Dist 目录。
rem
rem  本仓库每个 release 里有多个包（每个 GraalVM/JDK + OpenJFX 组合一格），
rem  所以本脚本支持挑选：
rem
rem  用法：
rem    Update.cmd                列出最新 release 里的所有包，按提示输入编号选择
rem    Update.cmd jdk23          按关键字挑选（这里选 jdk23 那一格的包）
rem    Update.cmd 25.0.4         也可以按 OpenJFX 版本关键字挑
rem    Update.cmd stable         只认正式版发布（默认包含预发行版）
rem
rem  说明：发布包内是 lcd-repro.exe（原生镜像）+ lcd-repro.jar（JVM 版）+ 两个
rem        双击入口脚本 + RESULT.txt；解压后直接覆盖到本目录。
rem
rem  本脚本**不需要管理员权限**。若提示写入失败，说明文件正被占用
rem  （例如 lcd-repro.exe 还在运行），先关掉再重试。
rem
rem  编码约定（重要）：本文件必须保存为 CRLF 行尾 + UTF-8 编码（无 BOM）。
rem        若被编辑器改成 LF 行尾，cmd 解析含中文的行时会发生字节错位。
rem        同一约定见主仓库 Docs/DevelopmentConventions.md 第 8 条。
rem ==========================================================================

setlocal

rem ------------------------------- 配置区 -----------------------------------
rem 系统工具一律写绝对路径调用，避免被 PATH 中的同名程序遮蔽
set "PS_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"

rem 干活的是同目录下的 .ps1：查询发布、挑包、下载、解压、覆盖
set "PS1=%~dp0Update.ps1"

rem 传参：第一个参数 stable 只取正式版；其余参数作为包名关键字
set "MODE="
set "PICK="
if /i "%~1"=="stable" (
    set "MODE=-Stable"
) else (
    if not "%~1"=="" set "PICK=-Match "%~1""
)
rem ---------------------------------------------------------------------------

cd /d "%~dp0"

if not exist "%PS1%" (
    echo.
    echo [错误] 未找到下载脚本：
    echo        %PS1%
    echo.
    echo        Update.cmd 与 Update.ps1 必须放在同一目录。
    echo.
    pause
    exit /b 1
)

echo 正在检查 GitHub 上的最新构建包，请稍候...
echo.

"%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -File "%PS1%" %MODE% %PICK%
