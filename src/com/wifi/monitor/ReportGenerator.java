package com.wifi.monitor;

import java.util.ArrayList;
import java.util.List;

public class ReportGenerator {
    private final UsageDAO usageDAO = new UsageDAO();
    private final DeviceDAO deviceDAO = new DeviceDAO();

    /**
     * Checks which devices have exceeded their assigned data limits.
     * @return List of alert message strings.
     */
    public List<String> getLimitAlerts() {
        List<String> alerts = new ArrayList<>();
        List<Device> devices = deviceDAO.getAllDevices();
        for (Device d : devices) {
            double totalUsed = usageDAO.getTotalUsageByDevice(d.getDeviceId());
            if (totalUsed > d.getDataLimit()) {
                alerts.add(String.format("⚠️ '%s' (Owner: %s) exceeded limit! Used: %.2f / %.2f GB",
                        d.getDeviceName(), d.getOwnerName(), totalUsed, d.getDataLimit()));
            }
        }
        return alerts;
    }

    /**
     * Calculates the total data consumption across all devices in the usage records.
     * @return Total data used in GB.
     */
    public double getTotalConsumption() {
        double total = 0.0;
        List<UsageRecord> records = usageDAO.getAllUsageRecords();
        for (UsageRecord r : records) {
            total += r.getDataUsed();
        }
        return total;
    }
}