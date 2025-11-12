package com.nas_backend.model.config;

public class AppConfig {
    private StorageConfig storage;
    private BackupConfig backup;
    private ServerConfig server;
    private TrashCanConfig trashcan;

    // Empty constructor is required by Jackson
    public AppConfig() {
    }

    // Getters
    public StorageConfig getStorage() {
        return storage;
    }

    public BackupConfig getBackup() {
        return backup;
    }

    public ServerConfig getServer() {
        return server;
    }

    public TrashCanConfig getTrashCan() {
        return trashcan;
    }

    // Setters

    public void setStorage(StorageConfig storage) {
        this.storage = storage;
    }

    public void setBackup(BackupConfig backup) {
        this.backup = backup;
    }

    public void setServer(ServerConfig server) {
        this.server = server;
    }

    public void setTrashCan(TrashCanConfig trashcan) {
        this.trashcan = trashcan;
    }
}