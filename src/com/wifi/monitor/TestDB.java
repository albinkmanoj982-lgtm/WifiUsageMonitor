package com.wifi.monitor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class TestDB {
    public static void main(String[] args) {
        System.out.println("Testing Database Connection to wifi_usage_monitor...");

        // Using the exact table and column names from your phpMyAdmin screenshot
        String query = "SELECT device_id, device_name, owner_name, device_type, data_limit FROM devices";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            if (conn == null) {
                System.out.println("❌ Database Test Failed: Connection is null. Please make sure MySQL Server is running!");
                return;
            }
            System.out.println("✅ Database Connection Successful!\n");

            try (PreparedStatement pstmt = conn.prepareStatement(query);
                 ResultSet rs = pstmt.executeQuery()) {

                System.out.println("✅ Database Connection Successful!\n");
                System.out.println("Fetching data from 'devices' table...");
                System.out.println("------------------------------------------------------");

                // Loop through the results and print them
                while (rs.next()) {
                    int id = rs.getInt("device_id");
                    String name = rs.getString("device_name");
                    String owner = rs.getString("owner_name");
                    String type = rs.getString("device_type");
                    double limit = rs.getDouble("data_limit");

                    System.out.println("Device ID   : " + id);
                    System.out.println("Device Name : " + name);
                    System.out.println("Owner Name  : " + owner);
                    System.out.println("Device Type : " + type);
                    System.out.println("Data Limit  : " + limit + " GB");
                    System.out.println("------------------------------------------------------");
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Database Test Failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }
    }
}
