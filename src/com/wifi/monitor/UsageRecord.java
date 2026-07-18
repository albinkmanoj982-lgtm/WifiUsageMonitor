package com.wifi.monitor;

public class UsageRecord {
    private int usageId;
    private int deviceId;
    private String usageDate;
    private double dataUsed;

    public UsageRecord() {
    }

    public UsageRecord(int usageId, int deviceId, String usageDate, double dataUsed) {
        this.usageId = usageId;
        this.deviceId = deviceId;
        this.usageDate = usageDate;
        this.dataUsed = dataUsed;
    }

    public int getUsageId() {
        return usageId;
    }

    public void setUsageId(int usageId) {
        this.usageId = usageId;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }

    public String getUsageDate() {
        return usageDate;
    }

    public void setUsageDate(String usageDate) {
        this.usageDate = usageDate;
    }

    public double getDataUsed() {
        return dataUsed;
    }

    public void setDataUsed(double dataUsed) {
        this.dataUsed = dataUsed;
    }
}
