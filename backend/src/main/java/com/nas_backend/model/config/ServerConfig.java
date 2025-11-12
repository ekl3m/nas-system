package com.nas_backend.model.config;

public class ServerConfig {
    private String hostname;
    private boolean enableHTTPS;
    private long tokenTTL;
    private int maxUploadSizeMB;
    private String logLevel;
    private int maxConcurrentUploads;

    // Empty constructor is required by Jackson
    public ServerConfig() {
    }

    // Getters

    public String getHostname() {
        return hostname;
    }

    public boolean isEnableHTTPS() {
        return enableHTTPS;
    }
    
    public long getTokenTTL() {
        return tokenTTL;
    }

    public int getMaxUploadSizeMB() {
        return maxUploadSizeMB;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public int getMaxConcurrentUploads() {
        return maxConcurrentUploads;
    }

    // Setters

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public void setEnableHttps(boolean enableHTTPS) {
        this.enableHTTPS = enableHTTPS;
    }

    public void setTokenTTL(long tokenTTL) {
        this.tokenTTL = tokenTTL;
    }

    public void setMaxUploadSizeMB(int maxUploadSizeMB) {
        this.maxUploadSizeMB = maxUploadSizeMB;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public void setMaxConcurrentUploads(int maxConcurrentUploads) {
        this.maxConcurrentUploads = maxConcurrentUploads;
    }
}