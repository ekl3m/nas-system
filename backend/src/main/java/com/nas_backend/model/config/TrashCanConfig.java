package com.nas_backend.model.config;

public class TrashCanConfig {
    private boolean enabled;
    private int quotaGB;
    private int retentionDays;

    public TrashCanConfig() {
    }

    // Getters
    public boolean isEnabled() {
        return enabled;
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

    public void setQuotaGB(int quotaGB) {
        this.quotaGB = quotaGB;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
}