package com.wifi.monitor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UsageDAO {

    // 1. Method to ADD a new internet usage record
    public void addUsageRecord(int deviceId, String date, double dataUsed) {
        String query = "INSERT INTO usage_records (device_id, usage_date, data_used) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            if (conn != null) {
                pstmt.setInt(1, deviceId);
                pstmt.setString(2, date); // Date is passed as a String like "2026-07-16"
                pstmt.setDouble(3, dataUsed);

                pstmt.executeUpdate();
                System.out.println("Internet usage of " + dataUsed + " GB recorded successfully!");
            }
        } catch (SQLException e) {
            System.out.println("Error adding usage record: " + e.getMessage());
        }
    }

    // 1b. Method to INCREMENT a device's daily usage (for real-time tracking)
    public void incrementUsage(int deviceId, String date, double gbAmount) {
        String checkQuery = "SELECT usage_id FROM usage_records WHERE device_id = ? AND usage_date = ?";
        String updateQuery = "UPDATE usage_records SET data_used = data_used + ? WHERE usage_id = ?";
        String insertQuery = "INSERT INTO usage_records (device_id, usage_date, data_used) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn != null) {
                int usageId = -1;
                try (PreparedStatement pstmt = conn.prepareStatement(checkQuery)) {
                    pstmt.setInt(1, deviceId);
                    pstmt.setString(2, date);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            usageId = rs.getInt("usage_id");
                        }
                    }
                }

                if (usageId != -1) {
                    try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
                        pstmt.setDouble(1, gbAmount);
                        pstmt.setInt(2, usageId);
                        pstmt.executeUpdate();
                    }
                } else {
                    try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
                        pstmt.setInt(1, deviceId);
                        pstmt.setString(2, date);
                        pstmt.setDouble(3, gbAmount);
                        pstmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error incrementing usage: " + e.getMessage());
        }
    }

    // 2. Method to get the TOTAL data used by a specific device
    public double getTotalUsageByDevice(int deviceId) {
        String query = "SELECT SUM(data_used) AS TotalData FROM usage_records WHERE device_id = ?";
        double totalData = 0.0;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            if (conn != null) {
                pstmt.setInt(1, deviceId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        totalData = rs.getDouble("TotalData");
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error calculating total usage: " + e.getMessage());
        }

        return totalData;
    }

    // 3. Method to fetch ALL usage records
    public List<UsageRecord> getAllUsageRecords() {
        List<UsageRecord> records = new ArrayList<>();
        String query = "SELECT usage_id, device_id, usage_date, data_used FROM usage_records ORDER BY usage_date DESC, usage_id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {

            if (conn != null) {
                while (rs.next()) {
                    UsageRecord record = new UsageRecord(
                            rs.getInt("usage_id"),
                            rs.getInt("device_id"),
                            rs.getString("usage_date"),
                            rs.getDouble("data_used")
                    );
                    records.add(record);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching usage records: " + e.getMessage());
        }
        return records;
    }

    // 4. Method to print and return daily report formatted text
    public String getDailyReportText(String date) {
        String query = "SELECT r.device_id, d.device_name, d.owner_name, SUM(r.data_used) AS DailyUsage " +
                       "FROM usage_records r " +
                       "LEFT JOIN devices d ON r.device_id = d.device_id " +
                       "WHERE r.usage_date = ? " +
                       "GROUP BY r.device_id, d.device_name, d.owner_name";

        StringBuilder sb = new StringBuilder();
        sb.append("--- Daily Report for ").append(date).append(" ---\n");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            if (conn != null) {
                pstmt.setString(1, date);
                try (ResultSet rs = pstmt.executeQuery()) {
                    double grandTotal = 0.0;
                    boolean hasData = false;

                    while (rs.next()) {
                        hasData = true;
                        int devId = rs.getInt("device_id");
                        String name = rs.getString("device_name");
                        String owner = rs.getString("owner_name");
                        double dailyUsage = rs.getDouble("DailyUsage");
                        grandTotal += dailyUsage;

                        sb.append(String.format("Device ID: %d | Name: %s (%s) | Usage: %.2f GB\n", 
                                devId, name != null ? name : "Unknown", owner != null ? owner : "Unknown", dailyUsage));
                    }

                    if (!hasData) {
                        sb.append("No usage recorded for this date.\n");
                    } else {
                        sb.append("-----------------------------------\n");
                        sb.append(String.format("Total Network Usage Today: %.2f GB\n", grandTotal));
                    }
                }
            }
        } catch (SQLException e) {
            sb.append("Error generating report: ").append(e.getMessage()).append("\n");
        }
        return sb.toString();
    }
}