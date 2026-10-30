@echo off
:: Disable laptop proxy
echo ======================================================================
echo    Disable Laptop Interception Proxy
echo ======================================================================
echo.
reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyEnable /t REG_DWORD /d 0 /f >nul
echo [SUCCESS] Laptop proxy is now OFF. Standard direct internet restored!
echo ======================================================================
pause
