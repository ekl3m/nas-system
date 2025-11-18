package com.nas_backend.model.config;

public class ServerConfig {
    private String hostname;
    private long tokenTTL;
    private int maxUploadSizeMB;
    private int maxConcurrentUploads;
    private boolean enableEmailNotifications;
    private String adminEmail;

    // Empty constructor is required by Jackson
    public ServerConfig() {
    }

    // Getters

    public String getHostname() {
        return hostname;
    }
    
    public long getTokenTTL() {
        return tokenTTL;
    }

    public int getMaxUploadSizeMB() {
        return maxUploadSizeMB;
    }

    public int getMaxConcurrentUploads() {
        return maxConcurrentUploads;
    }

    public boolean isEnableEmailNotifications() {
        return enableEmailNotifications;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    // Setters

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public void setTokenTTL(long tokenTTL) {
        this.tokenTTL = tokenTTL;
    }

    public void setMaxUploadSizeMB(int maxUploadSizeMB) {
        this.maxUploadSizeMB = maxUploadSizeMB;
    }

    public void setMaxConcurrentUploads(int maxConcurrentUploads) {
        this.maxConcurrentUploads = maxConcurrentUploads;
    }

    public void setEnableEmailNotifications(boolean enableEmailNotifications) {
        this.enableEmailNotifications = enableEmailNotifications;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }
}