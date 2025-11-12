package com.nas_backend.model.config;

import java.util.List;

public class StorageConfig {
    private List<String> paths;
    private int quotaGB;

    // Empty constructor is required by Jackson
    public StorageConfig() {
    }

    // Getters
    public List<String> getPaths() {
        return paths;
    }

    public int getQuotaGB() {
        return quotaGB;
    }

    // Setters

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public void setQuotaGB(int quotaGB) {
        this.quotaGB = quotaGB;
    }
}