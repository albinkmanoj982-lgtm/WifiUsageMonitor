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
            java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM devices");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO devices (device_name, owner_name, device_type, data_limit, ip_address, mac_address, status, is_blocked, upload_gb, download_gb, bandwidth_limit_mbps, blocked_websites) VALUES " +
                    "('Workstation Pro', 'Alice Smith', 'Desktop', 100.0, '192.168.1.10', '00:1A:2B:3C:4D:5E', 'Connected', 0, 14.50, 48.20, 100.0, 'tiktok.com, casino.com')," +
                    "('MacBook Air', 'Bob Johnson', 'Laptop', 50.0, '192.168.1.14', '70:85:C2:A1:B2:C3', 'Connected', 0, 5.80, 29.40, 50.0, 'reddit.com')," +
                    "('iPhone 15', 'Alice Smith', 'Mobile', 30.0, '192.168.1.25', 'AC:DE:48:12:34:56', 'Connected', 0, 3.10, 18.90, 25.0, '')," +
                    "('Smart TV 4K', 'Living Room', 'Smart TV', 150.0, '192.168.1.40', 'B4:E6:2D:88:99:AA', 'Connected', 0, 1.20, 94.60, 80.0, '')," +
                    "('Smart Security Cam', 'Unknown/External', 'Other', 20.0, '192.168.1.88', 'F4:8C:50:AA:BB:CC', 'Connected', 0, 18.90, 2.30, 10.0, '')"
                );

                String today = java.time.LocalDate.now().toString();
                String yesterday = java.time.LocalDate.now().minusDays(1).toString();
                String dayBefore = java.time.LocalDate.now().minusDays(2).toString();

                stmt.executeUpdate("INSERT INTO usage_records (device_id, usage_date, data_used, upload_gb, download_gb) VALUES " +
                    "(1, '" + today + "', 6.50, 1.50, 5.00)," +
                    "(2, '" + today + "', 4.20, 0.80, 3.40)," +
                    "(3, '" + today + "', 2.10, 0.40, 1.70)," +
                    "(4, '" + today + "', 12.80, 0.30, 12.50)," +
                    "(5, '" + today + "', 14.20, 13.80, 0.40)," +
                    "(1, '" + yesterday + "', 8.40, 2.10, 6.30)," +
                    "(2, '" + yesterday + "', 5.10, 1.10, 4.00)," +
                    "(3, '" + yesterday + "', 3.00, 0.50, 2.50)," +
                    "(4, '" + yesterday + "', 15.60, 0.40, 15.20)," +
                    "(1, '" + dayBefore + "', 7.20, 1.80, 5.40)," +
                    "(2, '" + dayBefore + "', 4.80, 0.90, 3.90)"
                );

                stmt.executeUpdate("INSERT INTO website_restrictions (device_id, domain_name, category, action) VALUES " +
                    "(0, 'gambling-site.com', 'Gambling', 'BLOCK')," +
                    "(0, 'malware-host.net', 'Security Risk', 'BLOCK')," +
                    "(1, 'tiktok.com', 'Social Media', 'BLOCK')," +
                    "(2, 'torrent-downloads.org', 'P2P File Sharing', 'BLOCK')"
                );
            }
        } catch (SQLException ignored) {}
    }

    public static Connection getConnection() {
        if (!mysqlAvailable) return null;
        try {
            return DriverManager.getConnection(MYSQL_URL, USER, PASSWORD);
        } catch (Exception e) {
            mysqlAvailable = false;
            return null;
        }
    }
}
