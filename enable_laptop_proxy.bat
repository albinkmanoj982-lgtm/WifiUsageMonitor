@echo off
:: Enable laptop proxy for instant testing in Chrome/Edge on the laptop
echo ======================================================================
echo    Enable Laptop Interception Proxy (127.0.0.1:8081)
echo ======================================================================
echo.
reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyServer /t REG_SZ /d "127.0.0.1:8081" /f >nul
reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyEnable /t REG_DWORD /d 1 /f >nul
echo [SUCCESS] Laptop browser proxy is now ON (pointing to 127.0.0.1:8081).
echo.
echo Now open Chrome or Edge on this laptop and visit:
echo    http://youtube.com or http://chrome.com
echo.
echo You will immediately see the HTTP 403 Access Denied block notice,
echo and the real-time activity log will appear in the Java Desktop Dashboard!
echo.
echo (To turn it off later, double-click disable_laptop_proxy.bat)
echo ======================================================================
pause
