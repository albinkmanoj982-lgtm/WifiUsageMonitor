package com.wifi.monitor;

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Windows Firewall & System Hosts Enforcer
 * 
 * Provides kernel-level packet filtering via 'netsh advfirewall' and local PC
 * website blocking via 'C:\Windows\System32\drivers\etc\hosts'.
 */
public class WindowsFirewallManager {

    private static final String HOSTS_PATH = "C:\\Windows\\System32\\drivers\\etc\\hosts";
    private static final String HOSTS_START_MARKER = "# === WiFi Usage Monitor Blocklist (Auto-Generated) ===";
    private static final String HOSTS_END_MARKER   = "# === End WiFi Usage Monitor Blocklist ===";

    private static final Set<String> blockedIps = new CopyOnWriteArraySet<>();
    private static Boolean cachedIsElevated = null;

    /**
     * Checks if current process is running with Windows Administrator privileges.
     */
    public static synchronized boolean isElevated() {
        if (cachedIsElevated != null) return cachedIsElevated;
        try {
            Process process = Runtime.getRuntime().exec("net session");
            int exitCode = process.waitFor();
            cachedIsElevated = (exitCode == 0);
        } catch (Exception e) {
            cachedIsElevated = false;
        }
        return cachedIsElevated;
    }

    public static String getStatusMessage() {
        if (isElevated()) {
            return "Active (Kernel Firewall & Hosts Enforcement Enabled)";
        } else {
            return "Limited (Run start_app.bat as Administrator for Kernel Rules)";
        }
    }

    public static String getHostsStatusMessage() {
        if (isElevated()) {
            return "Active (C:\\Windows\\System32\\drivers\\etc\\hosts Sync Enabled)";
        } else {
            return "Read-Only (Requires Administrator to modify Hosts)";
        }
    }

    public static boolean isIpBlocked(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        return blockedIps.contains(ip.trim());
    }

    public static Set<String> getBlockedIps() {
        return Collections.unmodifiableSet(blockedIps);
    }

    /**
     * Blocks all incoming and outgoing network traffic for a specific device IP.
     */
    public static boolean blockDeviceIp(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) return false;
        String ip = ipAddress.trim();

        // Never block localhost or gateway loopbacks
        if (isLocalOrGatewayIp(ip)) {
            System.out.println("⚠️ Skipping firewall block for local/gateway IP: " + ip);
            return false;
        }

        blockedIps.add(ip);
        System.out.println("🚫 Device marked blocked in memory: " + ip);

        if (!isElevated()) {
            System.out.println("ℹ️ Administrator elevation required to apply Windows Firewall rule for " + ip + ". Device blocked via DNS & Proxy layers.");
            return true;
        }

        // Apply Windows Firewall rules (Inbound and Outbound)
        String ruleIn = "WiFiUsageMonitor_Block_" + sanitizeRuleName(ip) + "_In";
        String ruleOut = "WiFiUsageMonitor_Block_" + sanitizeRuleName(ip) + "_Out";

        executeNetshCommand("netsh advfirewall firewall add rule name=\"" + ruleIn + "\" dir=in action=block remoteip=" + ip + " profile=any");
        executeNetshCommand("netsh advfirewall firewall add rule name=\"" + ruleOut + "\" dir=out action=block remoteip=" + ip + " profile=any");

        System.out.println("🛡️ Windows Firewall rules created for blocked device: " + ip);
        return true;
    }

    /**
     * Unblocks network traffic for a previously blocked device IP.
     */
    public static boolean unblockDeviceIp(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) return false;
        String ip = ipAddress.trim();

        blockedIps.remove(ip);
        System.out.println("🟢 Device unblocked in memory: " + ip);

        if (!isElevated()) return true;

        String ruleIn = "WiFiUsageMonitor_Block_" + sanitizeRuleName(ip) + "_In";
        String ruleOut = "WiFiUsageMonitor_Block_" + sanitizeRuleName(ip) + "_Out";

        executeNetshCommand("netsh advfirewall firewall delete rule name=\"" + ruleIn + "\"");
        executeNetshCommand("netsh advfirewall firewall delete rule name=\"" + ruleOut + "\"");

        System.out.println("🛡️ Windows Firewall rules deleted for unblocked device: " + ip);
        return true;
    }

    /**
     * Synchronizes a list of devices with Windows Firewall rules.
     */
    public static void syncBlockedDevices(List<Device> devices) {
        if (devices == null) return;
        for (Device d : devices) {
            if (d.isBlocked() && d.getIpAddress() != null && !d.getIpAddress().isEmpty()) {
                blockDeviceIp(d.getIpAddress());
            } else if (!d.isBlocked() && d.getIpAddress() != null && blockedIps.contains(d.getIpAddress())) {
                unblockDeviceIp(d.getIpAddress());
            }
        }
    }

    /**
     * Synchronizes blocked website domains into Windows 'hosts' file.
     * This guarantees that blocked websites are IMMEDIATELY BLOCKED across all
     * web browsers (Chrome, Edge, Firefox, Brave) on the host PC itself!
     */
    public static synchronized boolean syncHostsFile(List<String> blockedDomains, Map<String, List<String>> aliases) {
        if (!isElevated()) {
            System.out.println("ℹ️ Notice: Windows Hosts file sync requires Administrator privileges. Run via start_app.bat as Admin.");
            return false;
        }

        File hostsFile = new File(HOSTS_PATH);
        if (!hostsFile.exists()) {
            System.out.println("⚠️ Windows hosts file not found at " + HOSTS_PATH);
            return false;
        }

        try {
            // Read existing lines
            List<String> originalLines = Files.readAllLines(Paths.get(HOSTS_PATH), StandardCharsets.UTF_8);
            List<String> preservedLines = new ArrayList<>();
            boolean insideBlock = false;

            for (String line : originalLines) {
                if (line.trim().equals(HOSTS_START_MARKER)) {
                    insideBlock = true;
                    continue;
                }
                if (line.trim().equals(HOSTS_END_MARKER)) {
                    insideBlock = false;
                    continue;
                }
                if (!insideBlock) {
                    preservedLines.add(line);
                }
            }

            // If there are blocked domains, build the generated block
            Set<String> allDomainEntries = new LinkedHashSet<>();
            if (blockedDomains != null && !blockedDomains.isEmpty()) {
                for (String d : blockedDomains) {
                    if (d == null) continue;
                    String clean = cleanDomain(d);
                    if (clean.isEmpty()) continue;

                    allDomainEntries.add(clean);
                    if (!clean.startsWith("www.")) {
                        allDomainEntries.add("www." + clean);
                    }
                    allDomainEntries.add("m." + clean);

                    // Add ecosystem aliases (e.g. googlevideo.com for youtube.com)
                    if (aliases != null) {
                        List<String> aliasList = aliases.get(clean);
                        if (aliasList != null) {
                            for (String a : aliasList) {
                                String cleanA = cleanDomain(a);
                                if (!cleanA.isEmpty()) {
                                    allDomainEntries.add(cleanA);
                                    if (!cleanA.startsWith("www.")) allDomainEntries.add("www." + cleanA);
                                }
                            }
                        }
                    }
                }
            }

            // Remove any trailing blank lines from preserved content
            while (!preservedLines.isEmpty() && preservedLines.get(preservedLines.size() - 1).trim().isEmpty()) {
                preservedLines.remove(preservedLines.size() - 1);
            }

            StringBuilder sb = new StringBuilder();
            for (String l : preservedLines) {
                sb.append(l).append("\r\n");
            }

            if (!allDomainEntries.isEmpty()) {
                sb.append("\r\n").append(HOSTS_START_MARKER).append("\r\n");
                for (String domain : allDomainEntries) {
                    sb.append("0.0.0.0 ").append(domain).append("\r\n");
                }
                sb.append(HOSTS_END_MARKER).append("\r\n");
            }

            // Make sure file is writable
            try {
                Runtime.getRuntime().exec("attrib -r " + HOSTS_PATH).waitFor();
            } catch (Exception ignored) {}

            Files.writeString(Paths.get(HOSTS_PATH), sb.toString(), StandardCharsets.UTF_8);
            System.out.println("✅ Windows Hosts file updated with " + allDomainEntries.size() + " sinkhole domain entries.");

            // Flush DNS resolver cache
            try {
                Runtime.getRuntime().exec("ipconfig /flushdns").waitFor();
                System.out.println("🔄 Windows DNS cache flushed (ipconfig /flushdns).");
            } catch (Exception ignored) {}

            return true;
        } catch (Exception e) {
            System.out.println("Notice updating hosts file: " + e.getMessage());
            return false;
        }
    }

    /**
     * Blocks Hotspot Private DNS over TLS (Port 853).
     * This forces connected Android/iOS phones to fall back to the Hotspot's standard
     * UDP Port 53 DNS (DnsFilterServer), preventing them from bypassing the domain blocklist.
     */
    public static void blockHotspotPrivateDns() {
        if (!isElevated()) return;
        executeNetshCommand("netsh advfirewall firewall add rule name=\"WiFiUsageMonitor_BlockPrivateDNS_In\" dir=in action=block protocol=TCP localport=853 remoteip=192.168.137.0/24");
        executeNetshCommand("netsh advfirewall firewall add rule name=\"WiFiUsageMonitor_BlockPrivateDNS_Out\" dir=out action=block protocol=TCP remoteport=853");
    }

    /**
     * Cleans up all firewall rules and hosts entries created by WiFi Usage Monitor.
     */
    public static void cleanupAllRules() {
        if (!isElevated()) return;
        try {
            executeNetshCommand("netsh advfirewall firewall delete rule name=\"WiFiUsageMonitor_BlockPrivateDNS_In\"");
            executeNetshCommand("netsh advfirewall firewall delete rule name=\"WiFiUsageMonitor_BlockPrivateDNS_Out\"");

            for (String ip : blockedIps) {
                String ruleIn = "WiFiUsageMonitor_Block_" + sanitizeRuleName(ip) + "_In";
                String ruleOut = "WiFiUsageMonitor_Block_" + sanitizeRuleName(ip) + "_Out";
                executeNetshCommand("netsh advfirewall firewall delete rule name=\"" + ruleIn + "\"");
                executeNetshCommand("netsh advfirewall firewall delete rule name=\"" + ruleOut + "\"");
            }

            // Clean hosts file
            syncHostsFile(Collections.emptyList(), Collections.emptyMap());
        } catch (Exception ignored) {}
    }

    /**
     * Relaunches the application with Windows Administrator elevation.
     */
    public static void relaunchAsAdmin() {
        try {
            String appDir = System.getProperty("user.dir");
            String batFile = appDir + "\\start_app.bat";
            if (new File(batFile).exists()) {
                String cmd = "Start-Process cmd -ArgumentList '/c \"\"" + batFile + "\"\"' -Verb RunAs";
                new ProcessBuilder("powershell", "-NoProfile", "-Command", cmd).start();
                System.out.println("⚡ Relaunching WiFi Usage Monitor as Administrator...");
                System.exit(0);
            }
        } catch (Exception e) {
            System.out.println("Notice relaunching as admin: " + e.getMessage());
        }
    }

    private static void executeNetshCommand(String commandLine) {
        try {
            Process process = Runtime.getRuntime().exec(commandLine);
            process.waitFor();
        } catch (Exception ignored) {}
    }

    private static boolean isLocalOrGatewayIp(String ip) {
        if (ip == null || ip.isEmpty()) return true;
        if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("localhost")) return true;
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            if (ip.equalsIgnoreCase(localHost.getHostAddress())) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private static String cleanDomain(String raw) {
        if (raw == null) return "";
        String d = raw.toLowerCase().trim();
        if (d.startsWith("http://")) d = d.substring(7);
        if (d.startsWith("https://")) d = d.substring(8);
        if (d.startsWith("ftp://")) d = d.substring(6);
        if (d.contains("/")) d = d.substring(0, d.indexOf("/"));
        if (d.contains(":")) d = d.substring(0, d.indexOf(":"));
        while (d.endsWith(".")) d = d.substring(0, d.length() - 1);
        return d.trim();
    }

    private static String sanitizeRuleName(String ip) {
        return ip.replace(".", "_").replace(":", "_");
    }
}
