package com.wifi.monitor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

    private static final String SERVER_URL = "jdbc:mysql://localhost:3306/";
    private static final String URL = "jdbc:mysql://localhost:3306/wifi_usage_monitor";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    public static void initializeDatabase() {
        // 1. Create database if it doesn't exist
        try (Connection conn = DriverManager.getConnection(SERVER_URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS wifi_usage_monitor");
        } catch (SQLException e) {
            System.out.println("Could not check/create database: " + e.getMessage());
        }

        // 2. Create tables if they don't exist
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            
            // User login credentials table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS login (" +
                "  username VARCHAR(50) PRIMARY KEY," +
                "  password VARCHAR(100) NOT NULL," +
                "  email VARCHAR(100) NOT NULL" +
                ")"
            );

            // Devices list table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS devices (" +
                "  device_id INT AUTO_INCREMENT PRIMARY KEY," +
                "  device_name VARCHAR(100) NOT NULL," +
                "  owner_name VARCHAR(100) NOT NULL," +
                "  device_type VARCHAR(50) NOT NULL," +
                "  data_limit DOUBLE NOT NULL" +
                ")"
            );

            // Usage records log table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS usage_records (" +
                "  usage_id INT AUTO_INCREMENT PRIMARY KEY," +
                "  device_id INT," +
                "  usage_date DATE NOT NULL," +
                "  data_used DOUBLE NOT NULL," +
                "  FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE" +
                ")"
            );
            System.out.println("✅ Database tables checked/created successfully.");
        } catch (SQLException e) {
            System.out.println("Error initializing database tables: " + e.getMessage());
        }
    }

    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (Exception e) {
            System.out.println("Database Connection Failed");
            e.printStackTrace();
            return null;
        }
    }
}
