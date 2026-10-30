package com.wifi.monitor;

public class Device {

    private int deviceId;
    private String deviceName;
    private String ownerName;
    private String deviceType;
    private double dataLimit;
    private String ipAddress;
    private String macAddress;
    private String status;
    private boolean isBlocked;
    private double uploadGb;
    private double downloadGb;
    private double bandwidthLimitMbps;
    private String blockedWebsites;

    public Device() {
        this.ipAddress = "192.168.1.100";
        this.macAddress = "AA:BB:CC:DD:EE:FF";
        this.status = "Connected";
        this.isBlocked = false;
        this.uploadGb = 0.0;
        this.downloadGb = 0.0;
        this.bandwidthLimitMbps = 100.0;
        this.blockedWebsites = "";
    }

    public Device(int deviceId, String deviceName, String ownerName, String deviceType, double dataLimit) {
        this(deviceId, deviceName, ownerName, deviceType, dataLimit, "192.168.1.100", "AA:BB:CC:DD:EE:FF", "Connected", false, 0.0, 0.0, 100.0, "");
    }

    public Device(int deviceId, String deviceName, String ownerName, String deviceType, double dataLimit,
                  String ipAddress, String macAddress, String status, boolean isBlocked,
                  double uploadGb, double downloadGb, double bandwidthLimitMbps, String blockedWebsites) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.ownerName = ownerName;
        this.deviceType = deviceType;
        this.dataLimit = dataLimit;
        this.ipAddress = (ipAddress != null && !ipAddress.isEmpty()) ? ipAddress : "192.168.1.100";
        this.macAddress = (macAddress != null && !macAddress.isEmpty()) ? macAddress : "AA:BB:CC:DD:EE:FF";
        this.status = (status != null && !status.isEmpty()) ? status : "Connected";
        this.isBlocked = isBlocked;
        this.uploadGb = uploadGb;
        this.downloadGb = downloadGb;
        this.bandwidthLimitMbps = bandwidthLimitMbps > 0 ? bandwidthLimitMbps : 100.0;
        this.blockedWebsites = blockedWebsites != null ? blockedWebsites : "";
    }

    public int getDeviceId() { return deviceId; }
    public void setDeviceId(int deviceId) { this.deviceId = deviceId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public double getDataLimit() { return dataLimit; }
    public void setDataLimit(double dataLimit) { this.dataLimit = dataLimit; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isBlocked() { return isBlocked; }
    public void setBlocked(boolean blocked) { isBlocked = blocked; }

    public double getUploadGb() { return uploadGb; }
    public void setUploadGb(double uploadGb) { this.uploadGb = uploadGb; }

    public double getDownloadGb() { return downloadGb; }
    public void setDownloadGb(double downloadGb) { this.downloadGb = downloadGb; }

    public double getTotalUsage() { return uploadGb + downloadGb; }

    public double getBandwidthLimitMbps() { return bandwidthLimitMbps; }
    public void setBandwidthLimitMbps(double bandwidthLimitMbps) { this.bandwidthLimitMbps = bandwidthLimitMbps; }

    public String getBlockedWebsites() { return blockedWebsites; }
    public void setBlockedWebsites(String blockedWebsites) { this.blockedWebsites = blockedWebsites; }
}
