@echo off
REM Native-image side: run THIS directory's lcd-repro.exe.
REM The exe path is resolved relative to this script (%~dp0), so a copy found
REM elsewhere on PATH is never used.
setlocal
cd /d "%~dp0"

if not exist "%~dp0lcd-repro.exe" (
    echo.
    echo [ERROR] lcd-repro.exe not found in this directory:
    echo         %~dp0lcd-repro.exe
    echo.
    echo         Run Dist\Update.cmd first to download the build package.
    echo.
    pause
    exit /b 1
)

"%~dp0lcd-repro.exe"
echo.
echo exit code: %ERRORLEVEL%
pause
