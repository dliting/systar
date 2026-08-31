@echo off
REM =====================================================
REM  start-backend — Start the Systar backend (port 8081)
REM
REM  Prerequisites: MySQL 8.x + db_systar database
REM  Build first:   bin\build-backend.bat
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

set BACKEND_PORT=8081
set FORCE=0

:parse_args
if "%~1"=="" goto end_parse_args
if "%~1"=="--force"   set FORCE=1
if "%~1"=="-f"        set FORCE=1
shift
goto parse_args
:end_parse_args

REM --- Check if already running ---
netstat -ano 2>nul | findstr ":%BACKEND_PORT%" | findstr "LISTENING" >nul
if !ERRORLEVEL! equ 0 (
    if !FORCE! equ 1 (
        echo [Backend] Already running — stopping first...
        call "%~dp0\stop-backend.bat"
    ) else (
        echo [Backend] Already running on :%BACKEND_PORT%.
        echo           Use --force to restart.
        exit /b 0
    )
)

REM --- Find jar ---
set JAR=extensions\systar-server\target\systar-server-1.0.0.jar
if not exist "!JAR!" (
    echo [Backend] JAR not found: !JAR!
    echo           Run bin\build-backend.bat to build first.
    exit /b 1
)

REM --- Start ---
echo [Backend] Starting on http://localhost:%BACKEND_PORT% ...
echo.
start /B java -Xms512m -Xmx1024m -jar "!JAR!" --spring.profiles.active=mysql

REM --- Wait for startup ---
echo [Backend] Waiting for startup...
set RETRIES=0
:health_loop
ping -n 1 -w 2000 localhost >nul
if exist "temp\backend.pid" (
    set /p PID=<"temp\backend.pid"
    echo [Backend] OK — PID !PID!, http://localhost:%BACKEND_PORT%
    goto :done
)
set /a RETRIES+=1
if !RETRIES! lss 15 goto health_loop
echo [Backend] FAILED — no response after 30s

:done
endlocal
