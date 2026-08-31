@echo off
REM =====================================================
REM  start-frontend — Start the Vite dev server (port 5173)
REM
REM  Options:
REM    --force / -f   Stop existing instance first
REM
REM  WARNING: This file MUST use CRLF line endings and
REM  ASCII encoding. LF-only line endings will cause
REM  Windows CMD to misparse commands. Do not convert
REM  line endings when editing.
REM =====================================================

setlocal enabledelayedexpansion

cd /d "%~dp0\.."

set FRONTEND_PORT=5173
set FORCE=0

:parse_args
if "%~1"=="" goto end_parse_args
if "%~1"=="--force"  set FORCE=1
if "%~1"=="-f"       set FORCE=1
shift
goto parse_args
:end_parse_args

REM --- Check if already running ---
netstat -ano 2>nul | findstr ":%FRONTEND_PORT%" | findstr "LISTENING" >nul
if !ERRORLEVEL! equ 0 (
    if !FORCE! equ 1 (
        echo [Frontend] Already running — stopping first...
        call "%~dp0\stop-frontend.bat"
    ) else (
        echo [Frontend] Already running on :%FRONTEND_PORT%.
        echo            Use --force to restart.
        exit /b 0
    )
)

REM --- Check node ---
where node >nul 2>nul
if !ERRORLEVEL! neq 0 (
    echo [Frontend] ERROR: node is not installed or not in PATH
    exit /b 1
)

REM --- Check node_modules ---
if not exist "frontend\node_modules" (
    echo [Frontend] Installing dependencies...
    cd frontend
    call npm install
    if !ERRORLEVEL! neq 0 (
        echo [Frontend] npm install FAILED
        exit /b 1
    )
    cd ..
)

REM --- Start ---
echo [Frontend] Starting on http://localhost:%FRONTEND_PORT% ...
echo.
start /B cmd /c "cd frontend && npm run dev"

REM --- Wait for health ---
echo [Frontend] Waiting for startup...
set RETRIES=0
:health_loop
ping -n 1 -w 2000 localhost >nul
curl -s -o NUL http://localhost:%FRONTEND_PORT% 2>nul
if !ERRORLEVEL! equ 0 (
    echo [Frontend] OK — http://localhost:%FRONTEND_PORT%
    goto :done
)
set /a RETRIES+=1
if !RETRIES! lss 10 goto health_loop
echo [Frontend] FAILED — no response after 20s

:done
endlocal
