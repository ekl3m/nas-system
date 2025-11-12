package com.nas_backend.model.config;

import java.util.List;

public class BackupConfig {
    private boolean enabled;
    private List<String> paths;
    private int quotaGB;

    // Empty constructor is required by Jackson
    public BackupConfig() {
    }

    // Getters
    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getPaths() {
        return paths;
    }

    public int getQuotaGB() {
        return quotaGB;
    }

    // Setters

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public void setQuotaGB(int quotaGB) {
        this.quotaGB = quotaGB;
    }
}
