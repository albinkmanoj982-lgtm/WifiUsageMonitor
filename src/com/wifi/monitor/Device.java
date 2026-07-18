package com.wifi.monitor;
public class Device {

    private int deviceId;
    private String deviceName;
    private String ownerName;
    private String deviceType;
    private double dataLimit;

    public Device() {
    }

    public Device(int deviceId, String deviceName,
                  String ownerName,
                  String deviceType,
                  double dataLimit) {

        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.ownerName = ownerName;
        this.deviceType = deviceType;
        this.dataLimit = dataLimit;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public double getDataLimit() {
        return dataLimit;
    }
}
