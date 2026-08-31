@echo off
setlocal enabledelayedexpansion

REM Systar Database Init (MySQL)
REM
REM Env vars: MYSQL_HOST, MYSQL_PORT, MYSQL_DB, MYSQL_USER, MYSQL_PASSWORD, MYSQL_BIN

if not defined MYSQL_HOST     set "MYSQL_HOST=localhost"
if not defined MYSQL_PORT     set "MYSQL_PORT=3306"
if not defined MYSQL_DB       set "MYSQL_DB=db_systar"
if not defined MYSQL_USER     set "MYSQL_USER=systar"
if not defined MYSQL_PASSWORD set "MYSQL_PASSWORD=123456"

set "SCRIPT_DIR=%~dp0"
set "DDL_DIR=%SCRIPT_DIR%ddl"
set "DATA_DIR=%SCRIPT_DIR%data"

if defined MYSQL_BIN (
    if not exist "%MYSQL_BIN%" (
        echo ERROR: MYSQL_BIN not found: %MYSQL_BIN%
        exit /b 1
    )
) else (
    set "MYSQL_BIN=mysql"
    where "!MYSQL_BIN!" >nul 2>&1
    if errorlevel 1 (
        echo ERROR: mysql not found. Set MYSQL_BIN to full path.
        exit /b 1
    )
)

echo === Systar Database Init ===
echo Target: %MYSQL_USER%@%MYSQL_HOST%:%MYSQL_PORT%/%MYSQL_DB%
echo.

echo [0] Dropping existing database...
"%MYSQL_BIN%" --default-character-set=utf8mb4 -h "%MYSQL_HOST%" -P "%MYSQL_PORT%" -u "%MYSQL_USER%" -p"%MYSQL_PASSWORD%" -e "DROP DATABASE IF EXISTS `%MYSQL_DB%`; CREATE DATABASE `%MYSQL_DB%` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;" 2>nul
if errorlevel 1 (
    echo ERROR: Drop/Create database failed
    exit /b 1
)

echo [1] Creating tables...
for %%f in ("%DDL_DIR%\*.sql") do (
    echo   Executing: %%~nxf
    "%MYSQL_BIN%" --default-character-set=utf8mb4 -h "%MYSQL_HOST%" -P "%MYSQL_PORT%" -u "%MYSQL_USER%" -p"%MYSQL_PASSWORD%" "%MYSQL_DB%" < "%%f"
    if errorlevel 1 (
        echo ERROR: DDL failed on %%~nxf
        exit /b 1
    )
)

echo [2] Loading seed data...
for %%f in ("%DATA_DIR%\*.sql") do (
    echo   Executing: %%~nxf
    "%MYSQL_BIN%" --default-character-set=utf8mb4 -h "%MYSQL_HOST%" -P "%MYSQL_PORT%" -u "%MYSQL_USER%" -p"%MYSQL_PASSWORD%" "%MYSQL_DB%" < "%%f"
    if errorlevel 1 (
        echo ERROR: Seed data failed on %%~nxf
        exit /b 1
    )
)

echo.
echo === Done ===
endlocal
