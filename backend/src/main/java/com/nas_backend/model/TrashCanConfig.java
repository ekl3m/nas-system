package com.nas_backend.model;

public class TrashcanConfig {
    private boolean enabled;
    private String path;
    private int quotaGB;
    private int retentionDays;

    public TrashcanConfig() {
    }

    // Getters
    public boolean isEnabled() {
        return enabled;
    }

    public String getPath() {
        return path;
    }

    public int getQuotaGB() {
        return quotaGB;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    // Setters

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setQuotaGB(int quotaGB) {
        this.quotaGB = quotaGB;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
}