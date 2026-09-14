package com.wifi.monitor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class UsageDAO {

    private static final List<UsageRecord> inMemoryRecords = new CopyOnWriteArrayList<>();
    private static final AtomicInteger usageIdCounter = new AtomicInteger(20);

    static {
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();
        String dayBefore = LocalDate.now().minusDays(2).toString();

        inMemoryRecords.add(new UsageRecord(1, 1, today, 6.50, 1.50, 5.00));
        inMemoryRecords.add(new UsageRecord(2, 2, today, 4.20, 0.80, 3.40));
        inMemoryRecords.add(new UsageRecord(3, 3, today, 2.10, 0.40, 1.70));
        inMemoryRecords.add(new UsageRecord(4, 4, today, 12.80, 0.30, 12.50));
        inMemoryRecords.add(new UsageRecord(5, 5, today, 14.20, 13.80, 0.40)); // Anomaly!
        inMemoryRecords.add(new UsageRecord(6, 1, yesterday, 8.40, 2.10, 6.30));
        inMemoryRecords.add(new UsageRecord(7, 2, yesterday, 5.10, 1.10, 4.00));
        inMemoryRecords.add(new UsageRecord(8, 3, yesterday, 3.00, 0.50, 2.50));
        inMemoryRecords.add(new UsageRecord(9, 4, yesterday, 15.60, 0.40, 15.20));
        inMemoryRecords.add(new UsageRecord(10, 1, dayBefore, 7.20, 1.80, 5.40));
        inMemoryRecords.add(new UsageRecord(11, 2, dayBefore, 4.80, 0.90, 3.90));
    }

    public void addUsageRecord(int deviceId, String date, double dataUsed) {
        addUsageRecord(deviceId, date, dataUsed, dataUsed * 0.25, dataUsed * 0.75);
    }

    public void addUsageRecord(int deviceId, String date, double dataUsed, double uploadGb, double downloadGb) {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            String query = "INSERT INTO usage_records (device_id, usage_date, data_used, upload_gb, download_gb) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, deviceId);
                pstmt.setString(2, date);
                pstmt.setDouble(3, dataUsed > 0 ? dataUsed : (uploadGb + downloadGb));
                pstmt.setDouble(4, uploadGb);
                pstmt.setDouble(5, downloadGb);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
            finally { try { conn.close(); } catch (Exception ignored) {} }
        }

        UsageRecord rec = new UsageRecord(usageIdCounter.incrementAndGet(), deviceId, date, dataUsed, uploadGb, downloadGb);
        inMemoryRecords.add(0, rec);
    }

    public void incrementUsage(int deviceId, String date, double gbAmount) {
        double ul = gbAmount * 0.25;
        double dl = gbAmount * 0.75;
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO usage_records (device_id, usage_date, data_used, upload_gb, download_gb) VALUES (?, ?, ?, ?, ?)")) {
                pstmt.setInt(1, deviceId);
                pstmt.setString(2, date);
                pstmt.setDouble(3, gbAmount);
                pstmt.setDouble(4, ul);
                pstmt.setDouble(5, dl);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
            finally { try { conn.close(); } catch (Exception ignored) {} }
        }

        inMemoryRecords.add(0, new UsageRecord(usageIdCounter.incrementAndGet(), deviceId, date, gbAmount, ul, dl));
    }

    public double getTotalUsageByDevice(int deviceId) {
        double total = 0;
        for (UsageRecord r : getAllUsageRecords()) {
            if (r.getDeviceId() == deviceId) total += r.getDataUsed();
        }
        return total;
    }

    public List<UsageRecord> getAllUsageRecords() {
        Connection conn = DatabaseConnection.getConnection();
        if (conn != null) {
            List<UsageRecord> records = new ArrayList<>();
            String query = "SELECT * FROM usage_records ORDER BY usage_date DESC, usage_id DESC";
            try (PreparedStatement pstmt = conn.prepareStatement(query);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    records.add(new UsageRecord(
                        rs.getInt("usage_id"),
                        rs.getInt("device_id"),
                        rs.getString("usage_date"),
                        rs.getDouble("data_used"),
                        rs.getDouble("upload_gb"),
                        rs.getDouble("download_gb")
                    ));
                }
                if (!records.isEmpty()) {
                    inMemoryRecords.clear();
                    inMemoryRecords.addAll(records);
                    return records;
                }
            } catch (SQLException ignored) {}
            finally { try { conn.close(); } catch (Exception ignored) {} }
        }

        return new ArrayList<>(inMemoryRecords);
    }

    public double getTodayTotalUsage() {
        String today = LocalDate.now().toString();
        double sum = 0;
        for (UsageRecord r : getAllUsageRecords()) {
            if (today.equalsIgnoreCase(r.getUsageDate())) sum += r.getDataUsed();
        }
        return sum;
    }

    public Map<String, Double> getOverallTrafficSplit() {
        Map<String, Double> map = new HashMap<>();
        double ul = 0, dl = 0, total = 0;
        for (UsageRecord r : getAllUsageRecords()) {
            ul += r.getUploadGb();
            dl += r.getDownloadGb();
            total += r.getDataUsed();
        }
        map.put("upload", ul);
        map.put("download", dl);
        map.put("total", total);
        return map;
    }

    public List<Map<String, Object>> getDailyStats(int daysLimit) {
        Map<String, double[]> agg = new TreeMap<>(); // date -> [upload, download, total]
        for (UsageRecord r : getAllUsageRecords()) {
            double[] v = agg.computeIfAbsent(r.getUsageDate(), k -> new double[3]);
            v[0] += r.getUploadGb();
            v[1] += r.getDownloadGb();
            v[2] += r.getDataUsed();
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : agg.entrySet()) {
            Map<String, Object> m = new HashMap<>();
            m.put("date", entry.getKey());
            m.put("upload", entry.getValue()[0]);
            m.put("download", entry.getValue()[1]);
            m.put("total", entry.getValue()[2]);
            list.add(m);
        }
        return list;
    }

    public String getDailyReportText(String date) {
        List<UsageRecord> records = getAllUsageRecords();
        DeviceDAO deviceDAO = new DeviceDAO();
        List<Device> devices = deviceDAO.getAllDevices();
        Map<Integer, Device> devMap = new HashMap<>();
        for (Device d : devices) devMap.put(d.getDeviceId(), d);

        StringBuilder sb = new StringBuilder();
        sb.append("====================================================\n");
        sb.append("         WIFI USAGE MONITOR — DAILY REPORT          \n");
        sb.append("         Report Date: ").append(date).append("\n");
        sb.append("====================================================\n\n");

        double grandTotal = 0, grandUpload = 0, grandDownload = 0;
        boolean hasData = false;

        Map<Integer, double[]> devTraffic = new HashMap<>(); // devId -> [ul, dl, total]
        for (UsageRecord r : records) {
            if (date.equalsIgnoreCase(r.getUsageDate())) {
                hasData = true;
                double[] tr = devTraffic.computeIfAbsent(r.getDeviceId(), k -> new double[3]);
                tr[0] += r.getUploadGb();
                tr[1] += r.getDownloadGb();
                tr[2] += r.getDataUsed();
                grandUpload += r.getUploadGb();
                grandDownload += r.getDownloadGb();
                grandTotal += r.getDataUsed();
            }
        }

        if (!hasData) {
            sb.append("No network traffic logged for date: ").append(date).append("\n");
        } else {
            for (Map.Entry<Integer, double[]> entry : devTraffic.entrySet()) {
                Device d = devMap.get(entry.getKey());
                String name = d != null ? d.getDeviceName() : "Unknown";
                String owner = d != null ? d.getOwnerName() : "Unknown";
                double[] tr = entry.getValue();

                sb.append(String.format("Device #%d: %s (%s)\n", entry.getKey(), name, owner));
                sb.append(String.format("   ├── Upload:   %.2f GB\n", tr[0]));
                sb.append(String.format("   ├── Download: %.2f GB\n", tr[1]));
                sb.append(String.format("   └── Total:    %.2f GB\n\n", tr[2]));
            }

            sb.append("----------------------------------------------------\n");
            sb.append(String.format("GRAND TOTAL UPLOAD:   %.2f GB\n", grandUpload));
            sb.append(String.format("GRAND TOTAL DOWNLOAD: %.2f GB\n", grandDownload));
            sb.append(String.format("NETWORK TOTAL USAGE:  %.2f GB\n", grandTotal));
            sb.append("----------------------------------------------------\n");
        }
        return sb.toString();
    }
}