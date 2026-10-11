@echo off
REM Native-image side: run this program as the compiled native image.
REM Opens a window; the judgement numbers are printed to this console too.
setlocal
cd /d "%~dp0"
lcd-repro.exe
echo.
echo exit code: %ERRORLEVEL%
pause
