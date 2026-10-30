@echo off
title WiFi Usage Monitor - Java Swing Desktop
echo ======================================================================
echo    WiFi Usage Monitor - Native Java Swing Desktop Application
echo ======================================================================
echo.
:: Check for Administrator privileges
net session >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [*] Administrator privileges required for Windows Firewall and Hosts enforcement.
    echo [*] Requesting UAC elevation...
    powershell -NoProfile -Command "Start-Process cmd -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs"
    exit /b
)

cd /d "%~dp0"

echo [*] Administrator privileges confirmed.
echo [*] Compiling latest Java source files...
javac -cp "lib/*" -d out src/com/wifi/monitor/*.java
echo [*] Starting WiFi Usage Monitor Desktop Application...
echo [*] Launching Java Swing Window...
echo.

java -cp "out;lib/*" com.wifi.monitor.Main

echo.
pause
