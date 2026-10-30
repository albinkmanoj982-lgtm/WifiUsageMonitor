package com.wifi.monitor;

import java.text.SimpleDateFormat;
import java.util.Date;

public class WebsiteRestriction {
    private int restrictionId;
    private int deviceId; // 0 for global network rule
    private String domainName;
    private String category;
    private String action; // BLOCK or THROTTLE
    private String status; // BLOCKED or UNBLOCKED
    private String createdAt;

    public WebsiteRestriction() {
        this.status = "BLOCKED";
        this.createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    public WebsiteRestriction(int restrictionId, int deviceId, String domainName, String category, String action) {
        this(restrictionId, deviceId, domainName, category, action, "BLOCKED", null);
    }

    public WebsiteRestriction(int restrictionId, int deviceId, String domainName, String category, String action, String status, String createdAt) {
        this.restrictionId = restrictionId;
        this.deviceId = deviceId;
        this.domainName = domainName;
        this.category = (category != null && !category.isEmpty()) ? category : "General";
        this.action = (action != null && !action.isEmpty()) ? action : "BLOCK";
        this.status = (status != null && !status.isEmpty()) ? status : "BLOCKED";
        this.createdAt = (createdAt != null && !createdAt.isEmpty()) 
            ? createdAt 
            : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    public int getRestrictionId() { return restrictionId; }
    public void setRestrictionId(int restrictionId) { this.restrictionId = restrictionId; }

    public int getDeviceId() { return deviceId; }
    public void setDeviceId(int deviceId) { this.deviceId = deviceId; }

    public String getDomainName() { return domainName; }
    public void setDomainName(String domainName) { this.domainName = domainName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isBlocked() {
        return "BLOCKED".equalsIgnoreCase(status);
    }
}
