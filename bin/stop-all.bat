@echo off
REM =====================================================
REM  stop-all — Stop frontend + backend
REM
REM  WARNING: This file MUST use CRLF line endings and
REM  ASCII encoding. LF-only line endings will cause
REM  Windows CMD to misparse commands. Do not convert
REM  line endings when editing.
REM =====================================================

setlocal enabledelayedexpansion

echo Stopping all services...
echo.

echo [Frontend]
call "%~dp0\stop-frontend.bat"

echo.
echo [Backend]
call "%~dp0\stop-backend.bat"

echo.
echo All services stopped.

endlocal
