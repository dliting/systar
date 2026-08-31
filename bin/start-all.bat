@echo off
REM =====================================================
REM  start-all — Start backend + frontend
REM
REM  Options:
REM    --force / -f   Stop existing instances first
REM
REM  WARNING: This file MUST use CRLF line endings and
REM  ASCII encoding. LF-only line endings will cause
REM  Windows CMD to misparse commands. Do not convert
REM  line endings when editing.
REM =====================================================

setlocal enabledelayedexpansion

cd /d "%~dp0\.."

set ARGS=
set FORCE=

REM Collect args for backend, track --force for frontend
:collect_args
if "%~1"=="" goto args_done
set ARGS=!ARGS! %~1
if "%~1"=="--force"  set FORCE=1
if "%~1"=="-f"       set FORCE=1
shift
goto collect_args
:args_done

echo =====================================================
echo   Systar Full Startup
echo =====================================================
echo.

echo [1/2] Starting backend...
call "%~dp0\start-backend.bat" !ARGS!
if !ERRORLEVEL! neq 0 exit /b !ERRORLEVEL!

echo.
echo [2/2] Starting frontend...
if defined FORCE (
    call "%~dp0\start-frontend.bat" --force
) else (
    call "%~dp0\start-frontend.bat"
)

echo.
echo =====================================================
echo   All services started
echo   Backend  : http://localhost:8081/api/monitor
echo   Frontend : http://localhost:5173
echo =====================================================

endlocal
