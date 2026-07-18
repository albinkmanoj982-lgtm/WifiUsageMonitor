package com.wifi.monitor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DeviceDAO {

    // 1. Method to ADD a new device to the MySQL database
    public void addDevice(Device device) {
        String query = "INSERT INTO devices (device_name, owner_name, device_type, data_limit) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            if (conn != null) {
                pstmt.setString(1, device.getDeviceName());
                pstmt.setString(2, device.getOwnerName());
                pstmt.setString(3, device.getDeviceType());
                pstmt.setDouble(4, device.getDataLimit());

                pstmt.executeUpdate();
                System.out.println("Device saved to database successfully!");
            }
        } catch (SQLException e) {
            System.out.println("Error adding device to database: " + e.getMessage());
        }
    }

    // 2. Method to REMOVE a device from the MySQL database
    public void removeDevice(int deviceId) {
        String deleteUsageQuery = "DELETE FROM usage_records WHERE device_id = ?";
        String deleteDeviceQuery = "DELETE FROM devices WHERE device_id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn != null) {
                conn.setAutoCommit(false);
                try (PreparedStatement pstmtUsage = conn.prepareStatement(deleteUsageQuery);
                     PreparedStatement pstmtDevice = conn.prepareStatement(deleteDeviceQuery)) {
                    
                    pstmtUsage.setInt(1, deviceId);
                    pstmtUsage.executeUpdate();

                    pstmtDevice.setInt(1, deviceId);
                    int rowsAffected = pstmtDevice.executeUpdate();
                    
                    conn.commit();
                    if (rowsAffected > 0) {
                        System.out.println("Device and its usage records removed from the database.");
                    } else {
                        System.out.println("Device ID not found in the database.");
                    }
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error removing device: " + e.getMessage());
        }
    }

    // 3. Method to GET all devices from the database
    public List<Device> getAllDevices() {
        List<Device> devices = new ArrayList<>();
        String query = "SELECT device_id, device_name, owner_name, device_type, data_limit FROM devices";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {

            if (conn != null) {
                while (rs.next()) {
                    Device device = new Device(
                        rs.getInt("device_id"),
                        rs.getString("device_name"),
                        rs.getString("owner_name"),
                        rs.getString("device_type"),
                        rs.getDouble("data_limit")
                    );
                    devices.add(device);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching all devices: " + e.getMessage());
        }
        return devices;
    }

    // 4. Method to UPDATE a device in the database
    public void updateDevice(Device device) {
        String query = "UPDATE devices SET device_name = ?, owner_name = ?, device_type = ?, data_limit = ? WHERE device_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            if (conn != null) {
                pstmt.setString(1, device.getDeviceName());
                pstmt.setString(2, device.getOwnerName());
                pstmt.setString(3, device.getDeviceType());
                pstmt.setDouble(4, device.getDataLimit());
                pstmt.setInt(5, device.getDeviceId());

                pstmt.executeUpdate();
                System.out.println("Device updated successfully!");
            }
        } catch (SQLException e) {
            System.out.println("Error updating device: " + e.getMessage());
        }
    }
}