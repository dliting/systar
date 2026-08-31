@echo off
REM =====================================================
REM  init-database — Drop and recreate the Systar database
REM
REM  WARNING: This destroys ALL data. Not for production.
REM  WARNING: This file MUST use CRLF line endings and
REM  ASCII encoding. LF-only line endings will cause
REM  Windows CMD to misparse commands. Do not convert
REM  line endings when editing.
REM =====================================================

cd /d "%~dp0\.."

set MYSQL_USER=systar
set MYSQL_PASS=123456
set MYSQL_DB=db_systar
set SCHEMA_SQL=extensions\systar-server\src\main\resources\schema\schema-mysql.sql
set DATA_SQL=extensions\systar-server\src\main\resources\schema\init-data-mysql.sql

echo =====================================================
echo   Database re-initialization
echo   DB: %MYSQL_DB% (user: %MYSQL_USER%)
echo =====================================================
echo.

REM Check mysql client
where mysql >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] mysql client not found in PATH
    echo         Install MySQL or add it to PATH.
    pause
    exit /b 1
)

echo [1/2] Dropping %MYSQL_DB%...
mysql -u%MYSQL_USER% -p%MYSQL_PASS% -e "DROP DATABASE IF EXISTS %MYSQL_DB%; CREATE DATABASE %MYSQL_DB% CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Failed to drop/create database.
    pause
    exit /b 1
)
echo         OK — database recreated.

echo [2/2] Running schema DDL...
mysql -u%MYSQL_USER% -p%MYSQL_PASS% %MYSQL_DB% < "%SCHEMA_SQL%" 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Schema DDL failed.
    pause
    exit /b 1
)
echo         OK — schema applied.

echo [3/3] Seeding data...
mysql -u%MYSQL_USER% -p%MYSQL_PASS% %MYSQL_DB% < "%DATA_SQL%" 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Data seeding failed.
    pause
    exit /b 1
)
echo         OK — data seeded.

echo.
echo =====================================================
echo   Database ready: %MYSQL_DB%
echo   You can now run: bin\start-backend.bat
echo =====================================================
