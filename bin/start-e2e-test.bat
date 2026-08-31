@echo off
REM =====================================================
REM  start-e2e-test — Start simulator + Systar for
REM  end-to-end integration testing.
REM
REM  Prerequisites:
REM    1. Build all:     ./mvnw clean package -DskipTests -o
REM    2. Simulator JAR: simulator/systar-simulator-server/target/systar-simulator-server-1.1.0.jar
REM    3. Systar JAR:    extensions/systar-server/target/systar-server-1.1.0.jar
REM
REM  Ports: Simulator REST=18080  Modbus=55502  OPC-UA=55503
REM          Systar=8081
REM =====================================================

setlocal enabledelayedexpansion
cd /d "%~dp0\.."

set SIM_JAR=simulator\systar-simulator-server\target\systar-simulator-server-1.1.0.jar
set SIM_PORT=18080
set MAX_WAIT=30

echo [E2E] ========================================
echo [E2E] Systar End-to-End Test Launcher
echo [E2E] ========================================
echo.

REM --- Check simulator JAR ---
if not exist "!SIM_JAR!" (
    echo [E2E] ERROR: Simulator JAR not found: !SIM_JAR!
    echo [E2E] Run: ./mvnw clean package -DskipTests -pl simulator -o
    exit /b 1
)

REM --- Start simulator ---
echo [E2E] Starting simulator...
start /B java -jar "!SIM_JAR!" > temp\simulator.log 2>&1

REM --- Wait for simulator health endpoint ---
echo [E2E] Waiting for simulator on port !SIM_PORT!...
set WAITED=0
:health_loop
ping -n 1 -w 1000 localhost >nul
curl -s http://localhost:!SIM_PORT!/api/health >nul 2>&1
if !ERRORLEVEL! equ 0 goto simulator_ready
set /a WAITED+=1
if !WAITED! lss !MAX_WAIT! goto health_loop
echo [E2E] ERROR: Simulator did not start within !MAX_WAIT! seconds
echo [E2E] Check temp\simulator.log for errors
taskkill /F /FI "WINDOWTITLE eq simulator*" >nul 2>&1
exit /b 1

:simulator_ready
echo [E2E] Simulator ready at http://localhost:!SIM_PORT!
echo.

REM --- Start Systar backend (H2 dev profile) ---
set SYSTAR_JAR=extensions\systar-server\target\systar-server-1.1.0.jar
if not exist "!SYSTAR_JAR!" (
    echo [E2E] ERROR: Systar JAR not found: !SYSTAR_JAR!
    echo [E2E] Run: ./mvnw clean package -DskipTests -o
    taskkill /F /FI "WINDOWTITLE eq simulator*" >nul 2>&1
    exit /b 1
)

echo [E2E] Starting Systar backend...
echo [E2E]   Simulator API:    http://localhost:18080
echo [E2E]   Simulator Modbus: localhost:55502
echo [E2E]   Simulator OPC-UA: opc.tcp://localhost:55503/systar-simulator
echo [E2E]   Systar Web:       http://localhost:8081
echo.
echo [E2E] Press Ctrl+C to stop both services.
echo.

java -Xms512m -Xmx1024m -jar "!SYSTAR_JAR!" --spring.profiles.active=dev

REM --- Cleanup on exit ---
echo [E2E] Stopping simulator...
taskkill /F /FI "WINDOWTITLE eq simulator*" >nul 2>&1
echo [E2E] Done.
endlocal
