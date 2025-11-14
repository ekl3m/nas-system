package com.nas_backend.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "file_nodes", indexes = {
        @Index(name = "idx_logical_path", columnList = "logicalPath", unique = true),
        @Index(name = "idx_parent_path", columnList = "parentPath"),
        @Index(name = "idx_mime_type", columnList = "mimeType")
})
public class FileNode {

    @Id // Key
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String logicalPath; // E.g. "admin/zdjecia/plaza.jpg"

    @Column(nullable = false)
    private String parentPath; // E.g. "admin/zdjecia" (for quicker listing)

    @Column(nullable = false)
    private String physicalPath; // E.g. "/mnt/dysk2/admin/plaza_hash_123.jpg"

    @Column(nullable = false)
    private String fileName; // E.g. "plaza.jpg"

    @Column(nullable = false)
    private boolean isDirectory;

    private long size;
    private Instant createdAt;
    private Instant modifiedAt;

    // Trash purposes
    private String restorePath; // Stores previous parent path after moving to trash

    private String mimeType; // E.g. "image/jpeg"

    // Empty constructor is required by JPA
    public FileNode() {
    }

    // Getters

    public Long getId() {
        return id;
    }

    public String getLogicalPath() {
        return logicalPath;
    }

    public String getParentPath() {
        return parentPath;
    }

    public String getPhysicalPath() {
        return physicalPath;
    }

    public String getFileName() {
        return fileName;
    }
    
    public boolean isDirectory() {
        return isDirectory;
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

    public String getRestorePath() {
        return restorePath;
    }

    public String getMimeType() {
        return mimeType;
    }

    // Setters

    public void setId(Long id) {
        this.id = id;
    }

    public void setLogicalPath(String logicalPath) {
        this.logicalPath = logicalPath;
    }

    public void setParentPath(String parentPath) {
        this.parentPath = parentPath;
    }

    public void setPhysicalPath(String physicalPath) {
        this.physicalPath = physicalPath;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setDirectory(boolean isDirectory) {
        this.isDirectory = isDirectory;
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

    public void setRestorePath(String restorePath) {
        this.restorePath = restorePath;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
}
