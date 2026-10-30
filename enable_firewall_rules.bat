@echo off
:: Batch script to configure Windows Firewall for WiFi Usage Monitor
:: Must be run as Administrator
setlocal enabledelayedexpansion

echo ======================================================================
echo    WiFi Usage Monitor -- Windows Firewall Configuration Tool
echo ======================================================================
echo.

net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [!] Requesting Administrator privileges...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

echo [*] Removing any old rules...

netsh advfirewall firewall delete rule name="WiFiMonitor_DNS" >nul 2>&1
netsh advfirewall firewall delete rule name="WiFiMonitor_DNS_TCP" >nul 2>&1
netsh advfirewall firewall delete rule name="WiFiMonitor_Proxy" >nul 2>&1
netsh advfirewall firewall delete rule name="WiFiMonitor_Web" >nul 2>&1

echo [*] Adding rule for Port 53 (DNS Interceptor UDP ^& TCP)...
netsh advfirewall firewall add rule name="WiFiMonitor_DNS" dir=in action=allow protocol=UDP localport=53 profile=any
netsh advfirewall firewall add rule name="WiFiMonitor_DNS_TCP" dir=in action=allow protocol=TCP localport=53 profile=any

echo [*] Adding rule for TCP Port 8081 (HTTP/HTTPS Proxy Interceptor)...
netsh advfirewall firewall add rule name="WiFiMonitor_Proxy" dir=in action=allow protocol=TCP localport=8081 profile=any

echo [*] Adding rule for TCP Port 8080 (Web Dashboard)...
netsh advfirewall firewall add rule name="WiFiMonitor_Web" dir=in action=allow protocol=TCP localport=8080 profile=any

echo.
echo ======================================================================
echo  [SUCCESS] All firewall rules have been added successfully!
echo  Phone B can now connect to Port 53 (DNS) and Port 8081 (Proxy).
echo ======================================================================
echo.
pause
