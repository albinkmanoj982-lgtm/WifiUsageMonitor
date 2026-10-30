package com.wifi.monitor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

    private static final String MYSQL_SERVER_URL = "jdbc:mysql://localhost:3306/";
    private static final String MYSQL_URL = "jdbc:mysql://localhost:3306/wifi_usage_monitor";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    private static boolean mysqlAvailable = false;
    private static boolean checkedMysql = false;

    public static synchronized void initializeDatabase() {
        if (checkedMysql) return;
        checkedMysql = true;

        try (Connection conn = DriverManager.getConnection(MYSQL_SERVER_URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS wifi_usage_monitor");
            mysqlAvailable = true;
        } catch (SQLException e) {
            System.out.println("ℹ️ MySQL (localhost:3306) not active. Using fast in-memory network data engine.");
            mysqlAvailable = false;
            return;
        }

        if (mysqlAvailable) {
            try (Connection conn = DriverManager.getConnection(MYSQL_URL, USER, PASSWORD);
                 Statement stmt = conn.createStatement()) {

                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS login (" +
                    "  username VARCHAR(50) PRIMARY KEY," +
                    "  password VARCHAR(100) NOT NULL," +
                    "  email VARCHAR(100) NOT NULL" +
                    ")"
                );

                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS devices (" +
                    "  device_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "  device_name VARCHAR(100) NOT NULL," +
                    "  owner_name VARCHAR(100) NOT NULL," +
                    "  device_type VARCHAR(50) NOT NULL," +
                    "  data_limit DOUBLE NOT NULL," +
                    "  ip_address VARCHAR(45) DEFAULT '192.168.1.100'," +
                    "  mac_address VARCHAR(50) DEFAULT 'AA:BB:CC:DD:EE:FF'," +
                    "  status VARCHAR(20) DEFAULT 'Connected'," +
                    "  is_blocked INT DEFAULT 0," +
                    "  upload_gb DOUBLE DEFAULT 0.0," +
                    "  download_gb DOUBLE DEFAULT 0.0," +
                    "  bandwidth_limit_mbps DOUBLE DEFAULT 100.0," +
                    "  blocked_websites TEXT" +
                    ")"
                );

                addColumnIfNotExists(stmt, "devices", "ip_address", "VARCHAR(45) DEFAULT '192.168.1.100'");
                addColumnIfNotExists(stmt, "devices", "mac_address", "VARCHAR(50) DEFAULT 'AA:BB:CC:DD:EE:FF'");
                addColumnIfNotExists(stmt, "devices", "status", "VARCHAR(20) DEFAULT 'Connected'");
                addColumnIfNotExists(stmt, "devices", "is_blocked", "INT DEFAULT 0");
                addColumnIfNotExists(stmt, "devices", "upload_gb", "DOUBLE DEFAULT 0.0");
                addColumnIfNotExists(stmt, "devices", "download_gb", "DOUBLE DEFAULT 0.0");
                addColumnIfNotExists(stmt, "devices", "bandwidth_limit_mbps", "DOUBLE DEFAULT 100.0");
                addColumnIfNotExists(stmt, "devices", "blocked_websites", "TEXT");

                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS usage_records (" +
                    "  usage_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "  device_id INT," +
                    "  usage_date DATE NOT NULL," +
                    "  data_used DOUBLE NOT NULL," +
                    "  upload_gb DOUBLE DEFAULT 0.0," +
                    "  download_gb DOUBLE DEFAULT 0.0" +
                    ")"
                );

                addColumnIfNotExists(stmt, "usage_records", "upload_gb", "DOUBLE DEFAULT 0.0");
                addColumnIfNotExists(stmt, "usage_records", "download_gb", "DOUBLE DEFAULT 0.0");

                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS website_restrictions (" +
                    "  restriction_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "  device_id INT DEFAULT 0," +
                    "  domain_name VARCHAR(150) NOT NULL," +
                    "  category VARCHAR(50) DEFAULT 'General'," +
                    "  action VARCHAR(20) DEFAULT 'BLOCK'," +
                    "  status VARCHAR(20) DEFAULT 'BLOCKED'," +
                    "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")"
                );
                addColumnIfNotExists(stmt, "website_restrictions", "status", "VARCHAR(20) DEFAULT 'BLOCKED'");
                addColumnIfNotExists(stmt, "website_restrictions", "created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");

                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS blocked_request_logs (" +
                    "  log_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "  domain_name VARCHAR(150) NOT NULL," +
                    "  client_ip VARCHAR(50) DEFAULT '127.0.0.1'," +
                    "  device_name VARCHAR(100) DEFAULT 'Hotspot Device'," +
                    "  action VARCHAR(20) DEFAULT 'BLOCKED'," +
                    "  blocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")"
                );

                seedSampleDataIfEmpty(stmt);
                System.out.println("✅ MySQL database initialized successfully.");
            } catch (SQLException e) {
                System.out.println("Notice initializing MySQL tables: " + e.getMessage());
            }
        }
    }

    private static void addColumnIfNotExists(Statement stmt, String tableName, String columnName, String columnDef) {
        try {
            stmt.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDef);
        } catch (SQLException ignored) {}
    }

    private static void seedSampleDataIfEmpty(Statement stmt) {
        try {
            java.sql.ResultSet devRs = stmt.executeQuery("SELECT COUNT(*) FROM devices");
            if (devRs.next() && devRs.getInt(1) <= 1) {
                stmt.executeUpdate("INSERT INTO devices (device_id, device_name, owner_name, device_type, data_limit, ip_address, mac_address, status, is_blocked, upload_gb, download_gb, bandwidth_limit_mbps, blocked_websites) VALUES " +
                    "(2, 'MacBook Pro 16\"', 'Sarah Chen', 'Laptop', 80.0, '192.168.1.115', 'A4:83:E7:22:90:5F', 'Connected', 0, 9.20, 28.50, 100.0, '')," +
                    "(3, 'iPhone 15 Pro', 'Alex Morgan', 'Mobile', 40.0, '192.168.1.120', 'DC:A9:04:77:33:1B', 'Connected', 0, 4.10, 18.25, 50.0, '')," +
                    "(4, 'Galaxy S24 Ultra (Phone B)', 'Guest User', 'Mobile', 30.0, '192.168.137.45', '88:AE:DD:3F:12:9A', 'Connected', 0, 3.40, 14.60, 35.0, '')," +
                    "(5, 'LG OLED 65\" 4K TV', 'Living Room Media', 'Smart TV', 150.0, '192.168.1.140', 'B4:E6:2D:5C:88:12', 'Connected', 0, 2.10, 92.40, 80.0, '')," +
                    "(6, 'PlayStation 5 Console', 'Gaming Den', 'Gaming', 100.0, '192.168.1.155', '70:9E:29:4D:EE:01', 'Connected', 0, 16.30, 118.50, 100.0, '')," +
                    "(7, 'EZVIZ 4K Security Cam', 'External Entrance', 'IoT Device', 25.0, '192.168.1.188', 'F4:8C:50:9B:AA:77', 'Connected', 0, 22.80, 0.45, 15.0, '')," +
                    "(8, 'iPad Air 5th Gen', 'Elena Rostova', 'Tablet', 30.0, '192.168.1.132', '9C:F3:87:66:10:44', 'Disconnected', 0, 1.20, 8.10, 40.0, '')," +
                    "(9, 'HomeAssistant IoT Gateway', 'Smart Automation', 'IoT Device', 10.0, '192.168.1.199', '00:1E:06:44:88:21', 'Connected', 0, 0.90, 1.80, 20.0, '') " +
                    "ON DUPLICATE KEY UPDATE device_name=VALUES(device_name)");
            }

            // Seed default website restrictions if none exist
            java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM website_restrictions");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO website_restrictions (device_id, domain_name, category, action) VALUES " +
                    "(0, 'gambling-site.com', 'Gambling', 'BLOCK')," +
                    "(0, 'malware-host.net', 'Security Risk', 'BLOCK')," +
                    "(0, 'tiktok.com', 'Social Media', 'BLOCK')," +
                    "(0, 'torrent-downloads.org', 'P2P File Sharing', 'BLOCK')"
                );
            }
        } catch (SQLException ignored) {}
    }

    public static boolean isMysqlAvailable() {
        if (mysqlAvailable) return true;
        try (Connection c = getConnection()) {
            return c != null && !c.isClosed();
        } catch (Exception e) {
            return false;
        }
    }

    public static Connection getConnection() {
        try {
            Connection conn = DriverManager.getConnection(MYSQL_URL, USER, PASSWORD);
            if (!mysqlAvailable) {
                mysqlAvailable = true;
                System.out.println("✅ Real-time connection to XAMPP MySQL (wifi_usage_monitor) active.");
            }
            return conn;
        } catch (Exception e) {
            mysqlAvailable = false;
            return null;
        }
    }
}
