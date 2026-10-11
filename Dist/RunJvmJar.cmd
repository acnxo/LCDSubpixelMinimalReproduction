@echo off
REM JVM side: run this program on the JVM (fat jar, JavaFX included).
REM Opens a window; the judgement numbers are printed to this console too.
setlocal
cd /d "%~dp0"
java -jar lcd-repro.jar
echo.
echo exit code: %ERRORLEVEL%
pause
