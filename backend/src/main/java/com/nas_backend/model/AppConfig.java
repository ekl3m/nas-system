package com.nas_backend.model;

public class AppConfig {
    private String storagePath;
    private long tokenTTL;
    private int maxUploadSizeMB;

    public AppConfig() {
        // Empty constructor is required by Jackson
    } 

    // Getters:

    public String getStoragePath() {
        return storagePath;
    }

    public long getTokenTTL() {
        return tokenTTL;
    }

    public int getMaxUploadSizeMB() {
        return maxUploadSizeMB;
    }

    // Setters:

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public void setTokenTTL(long tokenTTL) {
        this.tokenTTL = tokenTTL;
    }

    public void setMaxUploadSizeMB(int maxUploadSizeMB) {
        this.maxUploadSizeMB = maxUploadSizeMB;
    }
}
