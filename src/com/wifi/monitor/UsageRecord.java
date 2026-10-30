package com.wifi.monitor;

public class UsageRecord {
    private int usageId;
    private int deviceId;
    private String usageDate;
    private double dataUsed;
    private double uploadGb;
    private double downloadGb;

    public UsageRecord() {
    }

    public UsageRecord(int usageId, int deviceId, String usageDate, double dataUsed) {
        this(usageId, deviceId, usageDate, dataUsed, dataUsed * 0.2, dataUsed * 0.8);
    }

    public UsageRecord(int usageId, int deviceId, String usageDate, double dataUsed, double uploadGb, double downloadGb) {
        this.usageId = usageId;
        this.deviceId = deviceId;
        this.usageDate = usageDate;
        this.dataUsed = dataUsed > 0 ? dataUsed : (uploadGb + downloadGb);
        this.uploadGb = uploadGb;
        this.downloadGb = downloadGb;
    }

    public int getUsageId() { return usageId; }
    public void setUsageId(int usageId) { this.usageId = usageId; }

    public int getDeviceId() { return deviceId; }
    public void setDeviceId(int deviceId) { this.deviceId = deviceId; }

    public String getUsageDate() { return usageDate; }
    public void setUsageDate(String usageDate) { this.usageDate = usageDate; }

    public double getDataUsed() { return dataUsed; }
    public void setDataUsed(double dataUsed) { this.dataUsed = dataUsed; }

    public double getUploadGb() { return uploadGb; }
    public void setUploadGb(double uploadGb) { this.uploadGb = uploadGb; }

    public double getDownloadGb() { return downloadGb; }
    public void setDownloadGb(double downloadGb) { this.downloadGb = downloadGb; }
}
