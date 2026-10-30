-- ======================================================================
-- WiFi Usage Monitor -- Complete XAMPP MySQL Database Schema & Seed Data
-- Import this file in phpMyAdmin (http://localhost/phpmyadmin/)
-- ======================================================================

CREATE DATABASE IF NOT EXISTS `wifi_usage_monitor` 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE `wifi_usage_monitor`;

-- ----------------------------------------------------------------------
-- Table structure for: devices (Device Management Registry)
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `devices` (
  `device_id` INT AUTO_INCREMENT PRIMARY KEY,
  `device_name` VARCHAR(100) NOT NULL,
  `owner_name` VARCHAR(100) NOT NULL,
  `device_type` VARCHAR(50) NOT NULL DEFAULT 'Other',
  `data_limit` DOUBLE NOT NULL DEFAULT 50.0,
  `ip_address` VARCHAR(45) NOT NULL DEFAULT '192.168.1.100',
  `mac_address` VARCHAR(50) NOT NULL DEFAULT 'AA:BB:CC:DD:EE:FF',
  `status` VARCHAR(20) NOT NULL DEFAULT 'Connected',
  `is_blocked` TINYINT(1) NOT NULL DEFAULT 0,
  `upload_gb` DOUBLE NOT NULL DEFAULT 0.0,
  `download_gb` DOUBLE NOT NULL DEFAULT 0.0,
  `bandwidth_limit_mbps` DOUBLE NOT NULL DEFAULT 100.0,
  `blocked_websites` TEXT DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------
-- Table structure for: usage_records (Historical Bandwidth Records)
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `usage_records` (
  `usage_id` INT AUTO_INCREMENT PRIMARY KEY,
  `device_id` INT NOT NULL,
  `usage_date` DATE NOT NULL,
  `data_used` DOUBLE NOT NULL DEFAULT 0.0,
  `upload_gb` DOUBLE NOT NULL DEFAULT 0.0,
  `download_gb` DOUBLE NOT NULL DEFAULT 0.0,
  INDEX `idx_device_date` (`device_id`, `usage_date`),
  CONSTRAINT `fk_usage_device` FOREIGN KEY (`device_id`) REFERENCES `devices` (`device_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------
-- Table structure for: website_restrictions (Domain Firewall Rules)
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `website_restrictions` (
  `restriction_id` INT AUTO_INCREMENT PRIMARY KEY,
  `device_id` INT DEFAULT 0,
  `domain_name` VARCHAR(150) NOT NULL,
  `category` VARCHAR(50) DEFAULT 'General',
  `action` VARCHAR(20) DEFAULT 'BLOCK',
  `status` VARCHAR(20) DEFAULT 'BLOCKED',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------
-- Table structure for: blocked_request_logs (Real-Time Blocked Traffic)
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `blocked_request_logs` (
  `log_id` INT AUTO_INCREMENT PRIMARY KEY,
  `domain_name` VARCHAR(150) NOT NULL,
  `client_ip` VARCHAR(50) DEFAULT '127.0.0.1',
  `device_name` VARCHAR(100) DEFAULT 'Hotspot Device',
  `action` VARCHAR(20) DEFAULT 'BLOCKED',
  `blocked_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------
-- Table structure for: login (Admin / User Authentication)
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `login` (
  `username` VARCHAR(50) PRIMARY KEY,
  `password` VARCHAR(100) NOT NULL,
  `email` VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------
-- Default Login Account (admin / admin123)
-- ----------------------------------------------------------------------
INSERT INTO `login` (`username`, `password`, `email`) 
VALUES ('admin', 'admin123', 'admin@wifimonitor.local')
ON DUPLICATE KEY UPDATE `email` = VALUES(`email`);

-- ----------------------------------------------------------------------
-- Seed Initial Devices in Device Management Registry
-- (Matches the Network Dashboard Fleet)
-- ----------------------------------------------------------------------
INSERT INTO `devices` (`device_id`, `device_name`, `owner_name`, `device_type`, `data_limit`, `ip_address`, `mac_address`, `status`, `is_blocked`, `upload_gb`, `download_gb`, `bandwidth_limit_mbps`, `blocked_websites`) VALUES
(1, 'ASUS (Laptop)', 'ASUS', 'Laptop', 100.0, '10.3.1.88', 'A8:E2:91:C4:0A:68', 'Connected', 0, 1.94, 10.02, 150.0, ''),
(2, 'MacBook Pro 16\" (Laptop)', 'Sarah Chen', 'Laptop', 80.0, '192.168.1.115', 'A4:83:E7:22:90:5F', 'Connected', 0, 9.20, 28.50, 100.0, ''),
(3, 'iPhone 15 Pro (Mobile)', 'Alex Morgan', 'Mobile', 40.0, '192.168.1.120', 'DC:A9:04:77:33:1B', 'Connected', 0, 4.10, 18.25, 50.0, ''),
(4, 'Galaxy S24 Ultra (Phone B)', 'Guest User', 'Mobile', 30.0, '192.168.137.45', '88:AE:DD:3F:12:9A', 'Connected', 0, 3.40, 14.60, 35.0, ''),
(5, 'LG OLED 65\" 4K TV', 'Living Room Media', 'Smart TV', 150.0, '192.168.1.140', 'B4:E6:2D:5C:88:12', 'Connected', 0, 2.10, 92.40, 80.0, ''),
(6, 'PlayStation 5 Console', 'Gaming Den', 'Gaming', 100.0, '192.168.1.155', '70:9E:29:4D:EE:01', 'Connected', 0, 16.30, 118.50, 100.0, ''),
(7, 'EZVIZ 4K Security Cam', 'External Entrance', 'IoT Device', 25.0, '192.168.1.188', 'F4:8C:50:9B:AA:77', 'Connected', 0, 22.80, 0.45, 15.0, ''),
(8, 'iPad Air 5th Gen', 'Elena Rostova', 'Tablet', 30.0, '192.168.1.132', '9C:F3:87:66:10:44', 'Disconnected', 0, 1.20, 8.10, 40.0, ''),
(9, 'HomeAssistant IoT Gateway', 'Smart Automation', 'IoT Device', 10.0, '192.168.1.199', '00:1E:06:44:88:21', 'Connected', 0, 0.90, 1.80, 20.0, '')
ON DUPLICATE KEY UPDATE 
  `device_name` = VALUES(`device_name`),
  `owner_name` = VALUES(`owner_name`),
  `ip_address` = VALUES(`ip_address`),
  `mac_address` = VALUES(`mac_address`),
  `data_limit` = VALUES(`data_limit`);

-- ----------------------------------------------------------------------
-- Seed Default Website Restrictions
-- ----------------------------------------------------------------------
INSERT INTO `website_restrictions` (`domain_name`, `category`, `action`, `status`) VALUES
('youtube.com', 'Streaming', 'BLOCK', 'BLOCKED'),
('gambling-site.com', 'Gambling', 'BLOCK', 'BLOCKED'),
('malware-host.net', 'Security Risk', 'BLOCK', 'BLOCKED'),
('tiktok.com', 'Social Media', 'BLOCK', 'BLOCKED'),
('torrent-downloads.org', 'P2P File Sharing', 'BLOCK', 'BLOCKED')
ON DUPLICATE KEY UPDATE `domain_name` = VALUES(`domain_name`);
