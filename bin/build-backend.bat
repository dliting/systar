@echo off
REM =====================================================
REM  build-backend — Build the Systar backend jar
REM
REM  WARNING: This file MUST use CRLF line endings and
REM  ASCII encoding. LF-only line endings will cause
REM  Windows CMD to misparse commands. Do not convert
REM  line endings when editing.
REM =====================================================

cd /d "%~dp0\.."

echo [Build] Building systar-server...
mvn clean package -pl extensions/systar-server -am -DskipTests -q
if %ERRORLEVEL% neq 0 (
    echo [Build] FAILED
    exit /b 1
)
echo [Build] OK — extensions\systar-server\target\systar-server-1.0.0.jar
