@echo off
REM JVM side: run THIS directory's lcd-repro.jar.
REM The jar path is resolved relative to this script (%~dp0), so a copy found
REM elsewhere on PATH is never used. 'java' itself must be on PATH.
setlocal
cd /d "%~dp0"

if not exist "%~dp0lcd-repro.jar" (
    echo.
    echo [ERROR] lcd-repro.jar not found in this directory:
    echo         %~dp0lcd-repro.jar
    echo.
    echo         Run Dist\Update.cmd first to download the build package.
    echo.
    pause
    exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
    echo.
    echo [ERROR] java not found on PATH. A JRE/JDK is required to run the jar.
    echo.
    pause
    exit /b 1
)

java -jar "%~dp0lcd-repro.jar"
echo.
echo exit code: %ERRORLEVEL%
pause
