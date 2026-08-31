@echo off
REM =====================================================
REM  stop-backend — Stop the Systar backend (port 8081)
REM
REM  Tries PID file first, falls back to port scan.
REM
REM  WARNING: This file MUST use CRLF line endings and
REM  ASCII encoding. LF-only line endings will cause
REM  Windows CMD to misparse commands. Do not convert
REM  line endings when editing.
REM =====================================================

setlocal enabledelayedexpansion

cd /d "%~dp0\.."
set BACKEND_PORT=8081
set PID_FILE=temp\backend.pid

echo [Backend] Stopping...

REM --- Try PID file first ---
if exist "%PID_FILE%" (
    set /p PID=<"%PID_FILE%"
    if not "!PID!"=="" (
        taskkill /F /PID !PID! 2>nul
        if !ERRORLEVEL! equ 0 (
            echo [Backend] Stopped PID !PID! (PID file)
            del "%PID_FILE%" 2>nul
            goto :done
        )
    )
    del "%PID_FILE%" 2>nul
)

REM --- Fallback: port scan ---
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%BACKEND_PORT%" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a 2>nul
    echo [Backend] Stopped PID %%a (port %BACKEND_PORT%)
    goto :done
)

echo [Backend] Not running (no PID file, port %BACKEND_PORT% free)

:done
endlocal
