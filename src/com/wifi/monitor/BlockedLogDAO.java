package com.wifi.monitor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class BlockedLogDAO {

    public static class BlockedEvent {
        private final long logId;
        private final String domainName;
        private final String clientIp;
        private final String deviceName;
        private final String timestamp;
        private final String action;

        public BlockedEvent(long logId, String domainName, String clientIp, String deviceName, String timestamp, String action) {
            this.logId = logId;
            this.domainName = domainName;
            this.clientIp = clientIp != null ? clientIp : "127.0.0.1";
            this.deviceName = deviceName != null ? deviceName : "Unknown Device";
            this.timestamp = timestamp != null ? timestamp : new SimpleDateFormat("HH:mm:ss").format(new Date());
            this.action = action != null ? action : "BLOCKED";
        }

        public long getLogId() { return logId; }
        public String getDomainName() { return domainName; }
        public String getClientIp() { return clientIp; }
        public String getDeviceName() { return deviceName; }
        public String getTimestamp() { return timestamp; }
        public String getAction() { return action; }
    }

    private static final List<BlockedEvent> inMemoryEvents = new CopyOnWriteArrayList<>();
    private static final AtomicLong idGenerator = new AtomicLong(1);
    private static final int MAX_IN_MEMORY_LOGS = 200;

    static {
        // Initial sample blocked events for demo visibility across multiple network devices
        long nowMs = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

        inMemoryEvents.add(new BlockedEvent(idGenerator.getAndIncrement(), "tiktok.com", "192.168.137.45", "Galaxy S24 Ultra (Phone B)", sdf.format(new Date(nowMs - 120000)), "BLOCKED"));
        inMemoryEvents.add(new BlockedEvent(idGenerator.getAndIncrement(), "ads-tracker.network", "192.168.1.140", "LG OLED 65\" 4K TV", sdf.format(new Date(nowMs - 340000)), "BLOCKED"));
        inMemoryEvents.add(new BlockedEvent(idGenerator.getAndIncrement(), "torrent-downloads.org", "192.168.1.155", "PlayStation 5 Console", sdf.format(new Date(nowMs - 680000)), "BLOCKED"));
        inMemoryEvents.add(new BlockedEvent(idGenerator.getAndIncrement(), "gambling-site.com", "192.168.1.115", "MacBook Pro 16\"", sdf.format(new Date(nowMs - 1200000)), "BLOCKED"));
        inMemoryEvents.add(new BlockedEvent(idGenerator.getAndIncrement(), "malware-host.net", "192.168.1.188", "EZVIZ 4K Security Cam", sdf.format(new Date(nowMs - 2400000)), "BLOCKED"));
    }

    public BlockedEvent recordBlockedRequest(String domainName, String clientIp, String deviceName, String action) {
        String timeStr = new SimpleDateFormat("HH:mm:ss").format(new Date());
        long newId = idGenerator.getAndIncrement();
        BlockedEvent event = new BlockedEvent(newId, domainName, clientIp, deviceName, timeStr, action);

        // 1. In-memory buffer (instant availability)
        inMemoryEvents.add(0, event); // Add to head
        if (inMemoryEvents.size() > MAX_IN_MEMORY_LOGS) {
            inMemoryEvents.remove(inMemoryEvents.size() - 1);
        }

        // 2. MySQL persistence if connected
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            String query = "INSERT INTO blocked_request_logs (domain_name, client_ip, device_name, action) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, domainName);
                pstmt.setString(2, clientIp);
                pstmt.setString(3, deviceName);
                pstmt.setString(4, action);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {
            } finally {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }

        System.out.printf("[DNS/PROXY INTERCEPTOR] 🚫 BLOCKED | Device: %s (%s) | Domain: %s | Time: %s%n",
                deviceName, clientIp, domainName, timeStr);

        return event;
    }

    public List<BlockedEvent> getRecentLogs(int limit) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            List<BlockedEvent> list = new ArrayList<>();
            String query = "SELECT * FROM blocked_request_logs ORDER BY log_id DESC LIMIT ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, limit);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String time = rs.getString("blocked_at");
                        if (time != null && time.length() >= 19) {
                            time = time.substring(11, 19); // extract HH:mm:ss
                        }
                        list.add(new BlockedEvent(
                                rs.getLong("log_id"),
                                rs.getString("domain_name"),
                                rs.getString("client_ip"),
                                rs.getString("device_name"),
                                time,
                                rs.getString("action")
                        ));
                    }
                }
                if (!list.isEmpty()) {
                    return list;
                }
            } catch (SQLException ignored) {
            } finally {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }

        int end = Math.min(limit, inMemoryEvents.size());
        return new ArrayList<>(inMemoryEvents.subList(0, end));
    }

    public void clearLogs() {
        inMemoryEvents.clear();
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM blocked_request_logs")) {
                pstmt.executeUpdate();
            } catch (SQLException ignored) {
            } finally {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }
    }
}
