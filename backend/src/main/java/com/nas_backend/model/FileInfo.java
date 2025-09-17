package com.nas_backend.model;

public class FileInfo {
    private String name;
    private boolean directory;
    private long size;
    private String lastModified;
    private String parentPath;

    public FileInfo(String name, boolean directory, long size, String lastModified, String parentPath) {
        this.name = name;
        this.directory = directory;
        this.size = size;
        this.lastModified = lastModified;
        this.parentPath = parentPath;
    }

    // Getters:

    public String getName() {
        return name;
    }

    public boolean isDirectory() {
        return directory;
    }

    public long getSize() {
        return size;
    }

    public String getLastModified() {
        return lastModified;
    }

    public String getParentPath() {
        return parentPath;
    }

    // Setters:

    public void setName(String name) {
        this.name = name;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }

    public void setParentPath(String parentPath) {
        this.parentPath = parentPath;
    }
}
