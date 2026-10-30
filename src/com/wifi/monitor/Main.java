package com.wifi.monitor;

import javax.swing.*;
import java.awt.GraphicsEnvironment;

public class Main {

    private static ApiServer apiServer;

    public static DnsFilterServer getDnsFilterServer() {
        return ApiServer.dnsFilterServer;
    }

    public static HttpFilterProxy getHttpFilterProxy() {
        return ApiServer.httpFilterProxy;
    }

    public static ApiServer getApiServer() {
        return apiServer;
    }

    public static void main(String[] args) {
        System.out.println("📶 Initializing WiFi Usage Monitor System...");

        // 1. Initialize Database tables & in-memory fallbacks
        DatabaseConnection.initializeDatabase();

        // 2. Start Network Filtering Daemons (DNS Filter on UDP 53 + HTTP Proxy on TCP 8081)
        try {
            apiServer = new ApiServer();
            apiServer.start();
        } catch (Exception e) {
            System.out.println("Notice starting Network Filter Services: " + e.getMessage());
        }

        // 3. Initialize Windows Firewall & System Hosts Enforcer
        try {
            DeviceDAO devDAO = new DeviceDAO();
            WindowsFirewallManager.syncBlockedDevices(devDAO.getAllDevices());

            RestrictionDAO restDAO = new RestrictionDAO();
            restDAO.syncHostsFileWithCurrentRestrictions();

            WindowsFirewallManager.blockHotspotPrivateDns();

            // Register shutdown hook for clean firewall & hosts restoration
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("🛑 [SHUTDOWN] Application shutdown signal received (window closed or process terminated).");
                System.out.println("🧹 [SHUTDOWN] Cleaning up Windows Firewall rules and hosts entries on shutdown...");
                WindowsFirewallManager.cleanupAllRules();
                System.out.println("🧹 [SHUTDOWN] Cleanup complete. Process exiting.");
            }, "WiFiUsageMonitor-ShutdownHook"));
        } catch (Exception e) {
            System.out.println("Notice initializing Firewall Manager: " + e.getMessage());
        }

        // 4. Set native system look-and-feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // 5. Launch Native Java Swing GUI as the desktop application
        if (!GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(LoginSignupUI::new);
        } else {
            System.out.println("ℹ️ Running in headless environment. Background network filtering daemons active.");
        }
        System.out.println("🚀 WiFi Usage Monitor engine fully initialized and active.");
    }
}
