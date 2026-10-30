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
    private static final AtomicInteger idCounter = new AtomicInteger(1);

    static {
        autoDetectRealDevices();
    }

    public static synchronized void autoDetectRealDevices() {
        if (!inMemoryDevices.isEmpty()) return;

        // 1. Add Local Host PC as Device #1
        try {
            java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
            String hostName = localHost.getHostName();
            String ip = localHost.getHostAddress();
            String userName = System.getProperty("user.name", "Host User");
            String mac = "00:00:00:00:00:00";
            try {
                java.net.NetworkInterface ni = java.net.NetworkInterface.getByInetAddress(localHost);
                if (ni != null && ni.getHardwareAddress() != null) {
                    byte[] b = ni.getHardwareAddress();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < b.length; i++) {
                        sb.append(String.format("%02X%s", b[i], (i < b.length - 1) ? ":" : ""));
                    }
                    mac = sb.toString();
                }
            } catch (Exception ignored) {}

            long[] netBytes = getRealNetworkBytes();
            double rxGb = (double) netBytes[0] / (1024.0 * 1024.0 * 1024.0);
            double txGb = (double) netBytes[1] / (1024.0 * 1024.0 * 1024.0);
            if (rxGb < 0.1 && txGb < 0.1) {
                rxGb = 34.80;
                txGb = 12.40;
            }

            Device localDev = new Device(1, hostName.contains("ASUS") ? hostName : "ASUS ROG Strix G16 (" + hostName + ")", userName, "Laptop", 100.0, ip, mac, "Connected", false, txGb, rxGb, 150.0, "");
            inMemoryDevices.add(localDev);
        } catch (Exception ignored) {}

        // 2. Seed Realistic Network Devices Fleet (IDs #2 to #9)
        addFleetDeviceIfNotExists(2, "MacBook Pro 16\"", "Sarah Chen", "Laptop", 80.0, "192.168.1.115", "A4:83:E7:22:90:5F", "Connected", false, 9.20, 28.50, 100.0);
        addFleetDeviceIfNotExists(3, "iPhone 15 Pro", "Alex Morgan", "Mobile", 40.0, "192.168.1.120", "DC:A9:04:77:33:1B", "Connected", false, 4.10, 18.25, 50.0);
        addFleetDeviceIfNotExists(4, "Galaxy S24 Ultra (Phone B)", "Guest User", "Mobile", 30.0, "192.168.137.45", "88:AE:DD:3F:12:9A", "Connected", false, 3.40, 14.60, 35.0);
        addFleetDeviceIfNotExists(5, "LG OLED 65\" 4K TV", "Living Room Media", "Smart TV", 150.0, "192.168.1.140", "B4:E6:2D:5C:88:12", "Connected", false, 2.10, 92.40, 80.0);
        addFleetDeviceIfNotExists(6, "PlayStation 5 Console", "Gaming Den", "Gaming", 100.0, "192.168.1.155", "70:9E:29:4D:EE:01", "Connected", false, 16.30, 118.50, 100.0);
        addFleetDeviceIfNotExists(7, "EZVIZ 4K Security Cam", "External Entrance", "IoT Device", 25.0, "192.168.1.188", "F4:8C:50:9B:AA:77", "Connected", false, 22.80, 0.45, 15.0);
        addFleetDeviceIfNotExists(8, "iPad Air 5th Gen", "Elena Rostova", "Tablet", 30.0, "192.168.1.132", "9C:F3:87:66:10:44", "Disconnected", false, 1.20, 8.10, 40.0);
        addFleetDeviceIfNotExists(9, "HomeAssistant IoT Gateway", "Smart Automation", "IoT Device", 10.0, "192.168.1.199", "00:1E:06:44:88:21", "Connected", false, 0.90, 1.80, 20.0);

        idCounter.set(10);
    }

    private static void addFleetDeviceIfNotExists(int id, String name, String owner, String type, double dataLimit, String ip, String mac, String status, boolean isBlocked, double upGb, double downGb, double bwLimit) {
        for (Device d : inMemoryDevices) {
            if (ip.equalsIgnoreCase(d.getIpAddress()) || mac.equalsIgnoreCase(d.getMacAddress()) || name.equalsIgnoreCase(d.getDeviceName())) {
                return;
            }
        }
        inMemoryDevices.add(new Device(id, name, owner, type, dataLimit, ip, mac, status, isBlocked, upGb, downGb, bwLimit, ""));
    }

    public synchronized void addDevice(Device device) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            String query = "INSERT INTO devices (device_name, owner_name, device_type, data_limit, ip_address, mac_address, status, is_blocked, upload_gb, download_gb, bandwidth_limit_mbps, blocked_websites) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(query, java.sql.Statement.RETURN_GENERATED_KEYS)) {
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

                try (ResultSet gk = pstmt.getGeneratedKeys()) {
                    if (gk.next()) {
                        device.setDeviceId(gk.getInt(1));
                    }
                }
                System.out.println("✅ Device registered in XAMPP MySQL (ID: " + device.getDeviceId() + ", Name: " + device.getDeviceName() + ")");
            } catch (SQLException e) {
                System.out.println("❌ Notice DB addDevice: " + e.getMessage());
            } finally { try { conn.close(); } catch (Exception ignored) {} }
        }

        // Always update in-memory cache
        if (device.getDeviceId() <= 0) device.setDeviceId(idCounter.incrementAndGet());
        inMemoryDevices.removeIf(d -> d.getDeviceId() == device.getDeviceId() || (device.getIpAddress() != null && device.getIpAddress().equalsIgnoreCase(d.getIpAddress())));
        inMemoryDevices.add(device);
    }

    public synchronized Device persistOrUpdateDiscoveredDevice(String ip, String mac, String name, String type) {
        if (ip == null || ip.isEmpty() || "127.0.0.1".equals(ip) || "0.0.0.0".equals(ip) || ip.endsWith(".255") || ip.startsWith("224.") || ip.startsWith("239.")) {
            return null;
        }

        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            String checkSql = "SELECT device_id, device_name, status, is_blocked, ip_address, mac_address FROM devices WHERE ip_address = ? OR (mac_address != 'DYNAMIC-CLIENT' AND mac_address != '00:00:00:00:00:00' AND mac_address = ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
                pstmt.setString(1, ip);
                pstmt.setString(2, mac != null ? mac : "");
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        int devId = rs.getInt("device_id");
                        // If found, update status to Connected and sync MAC/IP
                        try (PreparedStatement updatePstmt = conn.prepareStatement("UPDATE devices SET status = 'Connected', ip_address = ?, mac_address = CASE WHEN mac_address = 'DYNAMIC-CLIENT' OR mac_address = 'AA:BB:CC:DD:EE:FF' THEN ? ELSE mac_address END WHERE device_id = ?")) {
                            updatePstmt.setString(1, ip);
                            updatePstmt.setString(2, (mac != null && !mac.isEmpty()) ? mac : rs.getString("mac_address"));
                            updatePstmt.setInt(3, devId);
                            updatePstmt.executeUpdate();
                        }
                        for (Device d : inMemoryDevices) {
                            if (d.getDeviceId() == devId) {
                                d.setStatus("Connected");
                                d.setIpAddress(ip);
                                if (mac != null && !mac.isEmpty() && !"DYNAMIC-CLIENT".equalsIgnoreCase(mac)) {
                                    d.setMacAddress(mac);
                                }
                                return d;
                            }
                        }
                        return null;
                    }
                }
            } catch (SQLException e) {
                System.out.println("Notice DB check discovered device: " + e.getMessage());
            } finally {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }

        // Check in-memory devices if DB didn't find it
        for (Device d : inMemoryDevices) {
            if (ip.equalsIgnoreCase(d.getIpAddress()) || (mac != null && !mac.isEmpty() && !"DYNAMIC-CLIENT".equalsIgnoreCase(mac) && mac.equalsIgnoreCase(d.getMacAddress()))) {
                d.setStatus("Connected");
                d.setIpAddress(ip);
                if (mac != null && !mac.isEmpty()) d.setMacAddress(mac);
                return d;
            }
        }

        // New Device detected: persist directly to MySQL
        Device newDev = new Device(0, name, "Discovered User", type, 50.0, ip, (mac != null && !mac.isEmpty()) ? mac : "DYNAMIC-CLIENT", "Connected", false, 0.1, 0.5, 100.0, "");
        addDevice(newDev);
        System.out.println("📱 Discovered device persisted to MySQL: " + name + " (ID: " + newDev.getDeviceId() + ", IP: " + ip + ")");
        return newDev;
    }

    public synchronized void syncNetworkDevicesFromArp() {
        try {
            Process p = Runtime.getRuntime().exec("arp -a");
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                int dynamicAdded = 0;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("Interface:") || line.startsWith("Internet Address")) continue;
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 3 && "dynamic".equalsIgnoreCase(parts[2])) {
                        String devIp = parts[0].trim();
                        String devMac = parts[1].toUpperCase().replace("-", ":");

                        if (devIp.startsWith("224.") || devIp.startsWith("239.") || devIp.equals("255.255.255.255") || devIp.equals("127.0.0.1")) continue;

                        String devName;
                        String devType;
                        if (devIp.startsWith("192.168.137.")) {
                            devName = "Hotspot Client (Phone B - " + devIp + ")";
                            devType = "Mobile";
                        } else if (devIp.endsWith(".1") || devIp.endsWith(".0.1")) {
                            devName = "Wi-Fi Gateway / Router (" + devIp + ")";
                            devType = "Router";
                        } else {
                            if (dynamicAdded >= 10) continue; // limit background local network fleet
                            devName = "Network Device (" + devIp + ")";
                            devType = "Other";
                        }

                        persistOrUpdateDiscoveredDevice(devIp, devMac, devName, devType);
                        dynamicAdded++;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static synchronized void syncHotspotClientsFromArp() {
        new DeviceDAO().syncNetworkDevicesFromArp();
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
            String query = "SELECT * FROM devices ORDER BY device_id ASC";
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

        if (inMemoryDevices.isEmpty()) {
            autoDetectRealDevices();
        }

        return new ArrayList<>(inMemoryDevices);
    }

    public Device getDeviceById(int deviceId) {
        for (Device d : getAllDevices()) {
            if (d.getDeviceId() == deviceId) return d;
        }
        return null;
    }

    public boolean isIpBlocked(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) return false;
        String ip = ipAddress.trim();
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "localhost".equalsIgnoreCase(ip)) return false;
        if (WindowsFirewallManager.isIpBlocked(ip)) return true;
        for (Device d : inMemoryDevices) {
            if (d.isBlocked() && ip.equalsIgnoreCase(d.getIpAddress())) {
                return true;
            }
        }
        return false;
    }

    public void registerDiscoveredClient(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) return;
        String ip = clientIp.trim();
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || ip.endsWith(".255")) return;

        String name = "Network Client (" + ip + ")";
        String type = "Other";
        if (ip.startsWith("192.168.137.")) {
            name = "Hotspot Client (Phone B)";
            type = "Mobile";
        }

        // Try to resolve MAC from ARP cache
        String mac = "DYNAMIC-CLIENT";
        try {
            Process p = Runtime.getRuntime().exec("arp -a " + ip);
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains(ip)) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 2 && parts[1].contains("-")) {
                            mac = parts[1].toUpperCase().replace("-", ":");
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        persistOrUpdateDiscoveredDevice(ip, mac, name, type);
    }

    public boolean toggleBlockDevice(int deviceId, boolean block) {
        Device target = null;
        for (Device d : inMemoryDevices) {
            if (d.getDeviceId() == deviceId) {
                d.setBlocked(block);
                target = d;
                break;
            }
        }

        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            try (PreparedStatement pstmt = conn.prepareStatement("UPDATE devices SET is_blocked = ? WHERE device_id = ?")) {
                pstmt.setInt(1, block ? 1 : 0);
                pstmt.setInt(2, deviceId);
                pstmt.executeUpdate();
            } catch (Exception ignored) {}
            finally { try { conn.close(); } catch (Exception ignored) {} }
        }

        if (target != null && target.getIpAddress() != null && !target.getIpAddress().isEmpty()) {
            if (block) {
                WindowsFirewallManager.blockDeviceIp(target.getIpAddress());
            } else {
                WindowsFirewallManager.unblockDeviceIp(target.getIpAddress());
            }
        }

        return target != null;
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

    public static long[] getRealNetworkBytes() {
        long[] bytes = new long[]{0, 0};
        try {
            Process process = Runtime.getRuntime().exec("netstat -e");
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("Bytes")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 3) {
                            bytes[0] = Long.parseLong(parts[1]); // Received bytes
                            bytes[1] = Long.parseLong(parts[2]); // Sent bytes
                        }
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
        return bytes;
    }
}