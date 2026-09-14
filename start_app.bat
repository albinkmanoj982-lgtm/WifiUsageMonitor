@echo off
title WiFi Usage Monitor
echo ======================================================================
echo    WiFi Usage Monitor System
echo ======================================================================
echo.
cd /d "%~dp0"
java -cp "out;lib/*" com.wifi.monitor.Main
pause
