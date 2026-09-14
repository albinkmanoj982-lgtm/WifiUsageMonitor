package com.wifi.monitor;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.sql.Connection;

public class Main {

    public static void main(String[] args) {
        System.out.println("📶 Initializing WiFi Usage Monitor System...");

        // Initialize Database tables & in-memory fallbacks
        DatabaseConnection.initializeDatabase();

        // Start the Web API + Static File Server on port 8080
        try {
            ApiServer apiServer = new ApiServer();
            apiServer.start();
        } catch (Exception e) {
            System.out.println("Warning: Could not start web server: " + e.getMessage());
        }

        // Launch Swing GUI only if non-headless environment
        if (!GraphicsEnvironment.isHeadless()) {
            try {
                SwingUtilities.invokeLater(LoginSignupUI::new);
            } catch (Exception e) {
                System.out.println("Notice: Swing UI launch skipped: " + e.getMessage());
            }
        } else {
            System.out.println("ℹ️ Running in headless server mode. Web application active on http://localhost:8080");
        }
    }
}
