package com.wifi.monitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiAnomalyDetector {

    private final DeviceDAO deviceDAO = new DeviceDAO();
    private final UsageDAO usageDAO = new UsageDAO();

    public static class AnomalyResult {
        public int deviceId;
        public String deviceName;
        public String macAddress;
        public String ipAddress;
        public String severity; // LOW, MEDIUM, HIGH, CRITICAL
        public String anomalyType;
        public int riskScore; // 0 - 100
        public String aiExplanation;
        public String suggestedAction;

        public AnomalyResult(int deviceId, String deviceName, String macAddress, String ipAddress,
                             String severity, String anomalyType, int riskScore,
                             String aiExplanation, String suggestedAction) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.macAddress = macAddress;
            this.ipAddress = ipAddress;
            this.severity = severity;
            this.anomalyType = anomalyType;
            this.riskScore = riskScore;
            this.aiExplanation = aiExplanation;
            this.suggestedAction = suggestedAction;
        }
    }

    public List<AnomalyResult> detectAnomalies() {
        List<AnomalyResult> anomalies = new ArrayList<>();
        List<Device> devices = deviceDAO.getAllDevices();
        List<UsageRecord> records = usageDAO.getAllUsageRecords();

        if (devices.isEmpty()) return anomalies;

        // Group usage records by device
        Map<Integer, List<UsageRecord>> deviceRecordsMap = new HashMap<>();
        for (UsageRecord r : records) {
            deviceRecordsMap.computeIfAbsent(r.getDeviceId(), k -> new ArrayList<>()).add(r);
        }

        for (Device d : devices) {
            List<UsageRecord> devRecords = deviceRecordsMap.getOrDefault(d.getDeviceId(), new ArrayList<>());
            double totalUsed = d.getUploadGb() + d.getDownloadGb();
            if (totalUsed == 0.0) {
                for (UsageRecord r : devRecords) totalUsed += r.getDataUsed();
            }

            // 1. Check for Suspicious Upload Exfiltration Spike
            double uploadGb = d.getUploadGb();
            double downloadGb = d.getDownloadGb();
            if (uploadGb > 8.0 && (downloadGb == 0 || uploadGb / Math.max(downloadGb, 0.1) > 4.0)) {
                int risk = (int) Math.min(95, 70 + (uploadGb * 1.5));
                anomalies.add(new AnomalyResult(
                    d.getDeviceId(),
                    d.getDeviceName(),
                    d.getMacAddress(),
                    d.getIpAddress(),
                    "CRITICAL",
                    "Suspicious Data Exfiltration",
                    risk,
                    String.format("AI detected anomalous outbound upload burst of %.2f GB (Upload-to-Download ratio %.1fx). High risk of spyware or unauthorized data exfiltration.", uploadGb, uploadGb / Math.max(downloadGb, 0.1)),
                    "Block Device Immediately"
                ));
            }

            // 2. Check for Limit Breach Anomaly
            if (d.getDataLimit() > 0 && totalUsed > d.getDataLimit()) {
                double breachPct = ((totalUsed - d.getDataLimit()) / d.getDataLimit()) * 100;
                int risk = (int) Math.min(90, 50 + breachPct);
                anomalies.add(new AnomalyResult(
                    d.getDeviceId(),
                    d.getDeviceName(),
                    d.getMacAddress(),
                    d.getIpAddress(),
                    "HIGH",
                    "Data Limit Breach Surge",
                    risk,
                    String.format("Device consumed %.2f GB, exceeding its assigned limit of %.2f GB by %.1f%%. Bandwidth throttling recommended.", totalUsed, d.getDataLimit(), breachPct),
                    "Apply Speed Throttle (2 Mbps)"
                ));
            }

            // 3. Statistical Baseline Anomaly (Z-Score Analysis)
            if (devRecords.size() >= 3) {
                double sum = 0;
                for (UsageRecord r : devRecords) sum += r.getDataUsed();
                double mean = sum / devRecords.size();

                double varianceSum = 0;
                for (UsageRecord r : devRecords) varianceSum += Math.pow(r.getDataUsed() - mean, 2);
                double stdDev = Math.sqrt(varianceSum / devRecords.size());

                UsageRecord latestRecord = devRecords.get(0);
                if (stdDev > 0) {
                    double zScore = (latestRecord.getDataUsed() - mean) / stdDev;
                    if (zScore > 2.0 && latestRecord.getDataUsed() > 5.0) {
                        int risk = (int) Math.min(88, 60 + (zScore * 10));
                        anomalies.add(new AnomalyResult(
                            d.getDeviceId(),
                            d.getDeviceName(),
                            d.getMacAddress(),
                            d.getIpAddress(),
                            "MEDIUM",
                            "Unusual Traffic Pattern (Z-Score: " + String.format("%.2f", zScore) + ")",
                            risk,
                            String.format("Latest usage burst of %.2f GB is %.1fx standard deviations above device historical baseline (Average: %.2f GB).", latestRecord.getDataUsed(), zScore, mean),
                            "Monitor Traffic / Restrict Streaming"
                        ));
                    }
                }
            }

            // 4. Rogue / External Unregistered MAC Anomaly
            if (d.getOwnerName() != null && (d.getOwnerName().equalsIgnoreCase("Unknown") || d.getOwnerName().toLowerCase().contains("external"))) {
                anomalies.add(new AnomalyResult(
                    d.getDeviceId(),
                    d.getDeviceName(),
                    d.getMacAddress(),
                    d.getIpAddress(),
                    "HIGH",
                    "Unregistered / Rogue Device",
                    78,
                    String.format("Unidentified device '%s' (MAC: %s) actively communicating on network submask %s without verified owner credentials.", d.getDeviceName(), d.getMacAddress(), d.getIpAddress()),
                    "Isolate Device / Require Re-authentication"
                ));
            }
        }

        return anomalies;
    }

    public int calculateOverallNetworkRiskScore(List<AnomalyResult> list) {
        if (list.isEmpty()) return 12; // Healthy baseline network
        int maxRisk = 0;
        for (AnomalyResult a : list) {
            if (a.riskScore > maxRisk) maxRisk = a.riskScore;
        }
        return Math.min(99, maxRisk + (list.size() * 3));
    }
}
