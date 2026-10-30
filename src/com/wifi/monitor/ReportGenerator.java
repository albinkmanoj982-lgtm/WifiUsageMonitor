package com.wifi.monitor;

import java.util.ArrayList;
import java.util.List;

public class ReportGenerator {
    private final UsageDAO usageDAO = new UsageDAO();
    private final DeviceDAO deviceDAO = new DeviceDAO();

    public List<String> getLimitAlerts() {
        List<String> alerts = new ArrayList<>();
        List<Device> devices = deviceDAO.getAllDevices();
        for (Device d : devices) {
            double totalUsed = d.getUploadGb() + d.getDownloadGb();
            if (totalUsed == 0) totalUsed = usageDAO.getTotalUsageByDevice(d.getDeviceId());
            if (d.getDataLimit() > 0 && totalUsed > d.getDataLimit()) {
                alerts.add(String.format("⚠️ '%s' (Owner: %s) exceeded limit! Used: %.2f / %.2f GB",
                        d.getDeviceName(), d.getOwnerName(), totalUsed, d.getDataLimit()));
            } else if (d.isBlocked()) {
                alerts.add(String.format("🚫 Device '%s' (IP: %s) is BLOCKED by Administrator.",
                        d.getDeviceName(), d.getIpAddress()));
            }
        }
        return alerts;
    }

    public double getTotalConsumption() {
        double total = 0.0;
        List<UsageRecord> records = usageDAO.getAllUsageRecords();
        for (UsageRecord r : records) {
            total += r.getDataUsed();
        }
        return total;
    }

    public String generateCsvReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Record ID,Device ID,Device Name,Owner,IP Address,MAC Address,Usage Date,Upload (GB),Download (GB),Total Used (GB)\n");

        List<UsageRecord> records = usageDAO.getAllUsageRecords();
        List<Device> devices = deviceDAO.getAllDevices();
        java.util.Map<Integer, Device> devMap = new java.util.HashMap<>();
        for (Device d : devices) devMap.put(d.getDeviceId(), d);

        for (UsageRecord r : records) {
            Device d = devMap.get(r.getDeviceId());
            String name = d != null ? d.getDeviceName() : "Unknown";
            String owner = d != null ? d.getOwnerName() : "Unknown";
            String ip = d != null ? d.getIpAddress() : "192.168.1.X";
            String mac = d != null ? d.getMacAddress() : "AA:BB:CC:DD:EE:FF";

            sb.append(String.format("%d,%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%.2f,%.2f,%.2f\n",
                    r.getUsageId(), r.getDeviceId(), name, owner, ip, mac, r.getUsageDate(),
                    r.getUploadGb(), r.getDownloadGb(), r.getDataUsed()));
        }
        return sb.toString();
    }
}