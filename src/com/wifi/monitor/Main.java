package com.wifi.monitor;

import javax.swing.*;
import java.sql.Connection;

public class Main {

    public static void main(String[] args) {
        // Initialize Database and Tables
        DatabaseConnection.initializeDatabase();

        // Test connection
        Connection con = DatabaseConnection.getConnection();

        if (con != null) {
            System.out.println("Connected to MySQL Successfully. Launching Login Screen...");
            
            // Launch GUI on EDT
            SwingUtilities.invokeLater(LoginSignupUI::new);
        } else {
            System.out.println("Connection Failed");
            JOptionPane.showMessageDialog(null, 
                    "Failed to connect to the MySQL database.\n" +
                    "Please ensure XAMPP (MySQL) is running and the database 'wifi_usage_monitor' exists.", 
                    "Database Connection Error", 
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}
