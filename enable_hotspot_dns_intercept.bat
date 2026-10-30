@echo off
title Route Hotspot DNS to Java DnsFilterServer
echo ======================================================================
echo    WiFi Usage Monitor -- Route Hotspot DNS to Java Engine
echo ======================================================================
echo.

net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [!] Requesting Administrator privileges...
    powershell -NoProfile -Command "Start-Process cmd -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs"
    exit /b
)

cd /d "%~dp0"
set "BACKUP_FILE=%~dp0wifi_dns_backup.txt"

:: 1. Save or preserve Wi-Fi IPv4 DNS configuration
powershell -NoProfile -ExecutionPolicy Bypass -Command "& { $bf = '%BACKUP_FILE%'; if (Test-Path $bf) { $c = (Get-Content -Path $bf -Raw -ErrorAction SilentlyContinue).Trim(); if ($c -eq 'DHCP' -or ($c -match '^(\d{1,3}\.){3}\d{1,3}(,(\d{1,3}\.){3}\d{1,3})*$')) { Write-Host ('[*] Existing valid DNS backup found: ' + $c + ' (preserving existing backup).'); exit 0 } else { Write-Host '[!] Existing backup file is invalid or empty. Removing...'; Remove-Item -Path $bf -Force -ErrorAction SilentlyContinue } } Write-Host '[*] Detecting and saving current Wi-Fi IPv4 DNS configuration...'; $raw = (netsh interface ip show dns name='Wi-Fi' | Out-String); $serversObj = (Get-DnsClientServerAddress -InterfaceAlias 'Wi-Fi' -AddressFamily IPv4 -ErrorAction SilentlyContinue); $servers = if ($serversObj) { $serversObj.ServerAddresses } else { @() }; if ($raw -match '127\.0\.0\.1' -or ($servers -contains '127.0.0.1')) { Write-Host '[ERROR] Wi-Fi adapter is currently configured with 127.0.0.1 and no backup file exists.'; Write-Host '[ABORT] Cannot safely identify the original DNS configuration. Will not assume DHCP.'; exit 1 } $val = ''; if ($raw -match 'through DHCP' -and $raw -notmatch 'Statically Configured') { $val = 'DHCP' } elseif ($servers -and $servers.Count -gt 0) { $validIps = $true; foreach ($s in $servers) { if ($s -notmatch '^(\d{1,3}\.){3}\d{1,3}$') { $validIps = $false } } if ($validIps) { $val = ($servers -join ',') } } if ($val -eq 'DHCP' -or ($val -match '^(\d{1,3}\.){3}\d{1,3}(,(\d{1,3}\.){3}\d{1,3})*$')) { $val | Out-File -FilePath $bf -Encoding ascii -Force; if (Test-Path $bf) { $check = (Get-Content -Path $bf -Raw -ErrorAction SilentlyContinue).Trim(); if ($check -eq $val) { Write-Host ('[*] Current DNS configuration successfully backed up: ' + $check); exit 0 } } } Write-Host '[ERROR] Failed to safely identify or save Wi-Fi IPv4 DNS configuration.'; exit 1 }"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ======================================================================
    echo  [ERROR] Failed to identify or save a valid Wi-Fi DNS backup!
    echo  [ABORT] Aborting operation before making any DNS modifications.
    echo          Your network settings have not been altered.
    echo ======================================================================
    echo.
    pause
    exit /b 1
)

:: 2. Set Wi-Fi DNS to 127.0.0.1
echo [*] Pointing Wi-Fi adapter DNS to 127.0.0.1 (Local Java DnsFilterServer)...
netsh interface ip set dns name="Wi-Fi" static 127.0.0.1
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ======================================================================
    echo  [ERROR] netsh failed to set Wi-Fi adapter DNS to 127.0.0.1.
    echo  Preserving backup file for safety: %BACKUP_FILE%
    echo ======================================================================
    echo.
    pause
    exit /b 1
)

:: Verify that the DNS change actually took effect
powershell -NoProfile -ExecutionPolicy Bypass -Command "& { $raw = (netsh interface ip show dns name='Wi-Fi' | Out-String); if ($raw -match '127\.0\.0\.1') { exit 0 } else { exit 1 } }"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ======================================================================
    echo  [ERROR] Verification failed! Wi-Fi adapter is not reporting 127.0.0.1.
    echo  Preserving backup file for safety: %BACKUP_FILE%
    echo ======================================================================
    echo.
    pause
    exit /b 1
)
ipconfig /flushdns >nul 2>&1

echo.
echo ======================================================================
echo  [STATUS] Wi-Fi adapter DNS set to 127.0.0.1.
echo.
echo  [IMPORTANT VERIFICATION NOTICE]
echo  Setting the Wi-Fi adapter DNS to 127.0.0.1 configures the upstream
echo  DNS that Windows ICS queries for hotspot clients.
echo.
echo  This does NOT guarantee that Windows ICS will reliably forward all
echo  hotspot client DNS queries to Java across all network environments.
echo  Forwarding MUST be verified on an actual connected phone:
echo.
echo    1. Ensure Android Private DNS is set to OFF on the phone.
echo    2. Open a browser on the phone and attempt to visit a blocked site.
echo    3. Check the Java Dashboard query log to confirm the request was
echo       intercepted and received by DnsFilterServer.
echo.
echo  Original DNS backup saved to: %BACKUP_FILE%
echo  (Run disable_hotspot_dns_intercept.bat to safely restore your DNS)
echo ======================================================================
echo.
pause
