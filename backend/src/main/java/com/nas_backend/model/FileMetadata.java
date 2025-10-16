package com.nas_backend.model;

import java.time.Instant;

public class FileMetadata {
    private String logicalPath; // Path visible to user, for example "holidays/beach.jpg"
    private String physicalPath; // Full, physical path, for example "/mnt/disk1/holidays/beach.jpg"
    private long size;
    private Instant createdAt;
    private Instant modifiedAt;
    private boolean isDirectory;
    // Possible TO DO - ownerUserID

    public FileMetadata() {
    }

    public FileMetadata(String logicalPath, String physicalPath, long size, Instant createdAt, Instant modifiedAt, boolean isDirectory) {
        this.logicalPath = logicalPath;
        this.physicalPath = physicalPath;
        this.size = size;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        this.isDirectory = isDirectory;
    }

    // Getters

    public String getLogicalPath() {
        return logicalPath;
    }

    public String getPhysicalPath() {
        return physicalPath;
    }

    public long getSize() {
        return size;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    // Setters

    public void setLogicalPath(String logicalPath) {
        this.logicalPath = logicalPath;
    }

    public void setPhysicalPath(String physicalPath) {
        this.physicalPath = physicalPath;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setModifiedAt(Instant modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public void setDirectory(boolean isDirectory) {
        this.isDirectory = isDirectory;
    }
}