package com.nas_backend.model;

public class AppConfig {
    private StorageConfig storage;
    private BackupConfig backup;
    private ServerConfig server;
    private TrashCanConfig trashCan;

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
        return trashCan;
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

    public void setTrashCan(TrashCanConfig trashCan) {
        this.trashCan = trashCan;
    }
}