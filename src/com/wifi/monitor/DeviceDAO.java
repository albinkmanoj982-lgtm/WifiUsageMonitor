package com.wifi.monitor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class DeviceDAO {

    private static final List<Device> inMemoryDevices = new CopyOnWriteArrayList<>();
    private static final AtomicInteger idCounter = new AtomicInteger(10);

    static {
        // Default seed devices for fallback mode
        inMemoryDevices.add(new Device(1, "Workstation Pro", "Alice Smith", "Desktop", 100.0, "192.168.1.10", "00:1A:2B:3C:4D:5E", "Connected", false, 14.50, 48.20, 100.0, "tiktok.com, casino.com"));
        inMemoryDevices.add(new Device(2, "MacBook Air", "Bob Johnson", "Laptop", 50.0, "192.168.1.14", "70:85:C2:A1:B2:C3", "Connected", false, 5.80, 29.40, 50.0, "reddit.com"));
        inMemoryDevices.add(new Device(3, "iPhone 15", "Alice Smith", "Mobile", 30.0, "192.168.1.25", "AC:DE:48:12:34:56", "Connected", false, 3.10, 18.90, 25.0, ""));
        inMemoryDevices.add(new Device(4, "Smart TV 4K", "Living Room", "Smart TV", 150.0, "192.168.1.40", "B4:E6:2D:88:99:AA", "Connected", false, 1.20, 94.60, 80.0, ""));
        inMemoryDevices.add(new Device(5, "Smart Security Cam", "Unknown/External", "Other", 20.0, "192.168.1.88", "F4:8C:50:AA:BB:CC", "Connected", false, 18.90, 2.30, 10.0, ""));
    }

    public void addDevice(Device device) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            String query = "INSERT INTO devices (device_name, owner_name, device_type, data_limit, ip_address, mac_address, status, is_blocked, upload_gb, download_gb, bandwidth_limit_mbps, blocked_websites) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, device.getDeviceName());
                pstmt.setString(2, device.getOwnerName());
                pstmt.setString(3, device.getDeviceType());
                pstmt.setDouble(4, device.getDataLimit());
                pstmt.setString(5, device.getIpAddress());
                pstmt.setString(6, device.getMacAddress());
                pstmt.setString(7, device.getStatus());
                pstmt.setInt(8, device.isBlocked() ? 1 : 0);
                pstmt.setDouble(9, device.getUploadGb());
                pstmt.setDouble(10, device.getDownloadGb());
                pstmt.setDouble(11, device.getBandwidthLimitMbps());
                pstmt.setString(12, device.getBlockedWebsites());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.out.println("Notice DB addDevice: " + e.getMessage());
            } finally { try { conn.close(); } catch (Exception ignored) {} }
        }

        // Always update in-memory cache
        if (device.getDeviceId() <= 0) device.setDeviceId(idCounter.incrementAndGet());
        inMemoryDevices.add(device);
    }

    public void removeDevice(int deviceId) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            try {
                conn.setAutoCommit(false);
                try (PreparedStatement uPstmt = conn.prepareStatement("DELETE FROM usage_records WHERE device_id = ?");
                     PreparedStatement dPstmt = conn.prepareStatement("DELETE FROM devices WHERE device_id = ?")) {
                    uPstmt.setInt(1, deviceId); uPstmt.executeUpdate();
                    dPstmt.setInt(1, deviceId); dPstmt.executeUpdate();
                    conn.commit();
                } catch (SQLException e) { conn.rollback(); }
            } catch (Exception ignored) {}
            finally { try { conn.close(); } catch (Exception ignored) {} }
        }

        inMemoryDevices.removeIf(d -> d.getDeviceId() == deviceId);
    }

    public List<Device> getAllDevices() {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            List<Device> devices = new ArrayList<>();
            String query = "SELECT * FROM devices";
            try (PreparedStatement pstmt = conn.prepareStatement(query);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    devices.add(new Device(
                        rs.getInt("device_id"),
                        rs.getString("device_name"),
                        rs.getString("owner_name"),
                        rs.getString("device_type"),
                        rs.getDouble("data_limit"),
                        rs.getString("ip_address"),
                        rs.getString("mac_address"),
                        rs.getString("status"),
                        rs.getInt("is_blocked") == 1,
                        rs.getDouble("upload_gb"),
                        rs.getDouble("download_gb"),
                        rs.getDouble("bandwidth_limit_mbps"),
                        rs.getString("blocked_websites")
                    ));
                }
                if (!devices.isEmpty()) {
                    inMemoryDevices.clear();
                    inMemoryDevices.addAll(devices);
                    return devices;
                }
            } catch (SQLException e) {
                System.out.println("Notice DB getAllDevices: " + e.getMessage());
            } finally { try { conn.close(); } catch (Exception ignored) {} }
        }

        return new ArrayList<>(inMemoryDevices);
    }

    public Device getDeviceById(int deviceId) {
        for (Device d : getAllDevices()) {
            if (d.getDeviceId() == deviceId) return d;
        }
        return null;
    }

    public boolean toggleBlockDevice(int deviceId, boolean block) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            try (PreparedStatement pstmt = conn.prepareStatement("UPDATE devices SET is_blocked = ? WHERE device_id = ?")) {
                pstmt.setInt(1, block ? 1 : 0);
                pstmt.setInt(2, deviceId);
                pstmt.executeUpdate();
            } catch (Exception ignored) {}
            finally { try { conn.close(); } catch (Exception ignored) {} }
        }

        for (Device d : inMemoryDevices) {
            if (d.getDeviceId() == deviceId) {
                d.setBlocked(block);
                return true;
            }
        }
        return false;
    }

    public boolean setDeviceLimits(int deviceId, double dataLimitGb, double speedLimitMbps) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            try (PreparedStatement pstmt = conn.prepareStatement("UPDATE devices SET data_limit = ?, bandwidth_limit_mbps = ? WHERE device_id = ?")) {
                pstmt.setDouble(1, dataLimitGb);
                pstmt.setDouble(2, speedLimitMbps);
                pstmt.setInt(3, deviceId);
                pstmt.executeUpdate();
            } catch (Exception ignored) {}
            finally { try { conn.close(); } catch (Exception ignored) {} }
        }

        for (Device d : inMemoryDevices) {
            if (d.getDeviceId() == deviceId) {
                d.setDataLimit(dataLimitGb);
                d.setBandwidthLimitMbps(speedLimitMbps);
                return true;
            }
        }
        return false;
    }

    public boolean updateStatus(int deviceId, String status) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            try (PreparedStatement pstmt = conn.prepareStatement("UPDATE devices SET status = ? WHERE device_id = ?")) {
                pstmt.setString(1, status);
                pstmt.setInt(2, deviceId);
                pstmt.executeUpdate();
            } catch (Exception ignored) {}
            finally { try { conn.close(); } catch (Exception ignored) {} }
        }

        for (Device d : inMemoryDevices) {
            if (d.getDeviceId() == deviceId) {
                d.setStatus(status);
                return true;
            }
        }
        return false;
    }

    public boolean updateBlockedWebsites(int deviceId, String websites) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            try (PreparedStatement pstmt = conn.prepareStatement("UPDATE devices SET blocked_websites = ? WHERE device_id = ?")) {
                pstmt.setString(1, websites);
                pstmt.setInt(2, deviceId);
                pstmt.executeUpdate();
            } catch (Exception ignored) {}
            finally { try { conn.close(); } catch (Exception ignored) {} }
        }

        for (Device d : inMemoryDevices) {
            if (d.getDeviceId() == deviceId) {
                d.setBlockedWebsites(websites);
                return true;
            }
        }
        return false;
    }
}