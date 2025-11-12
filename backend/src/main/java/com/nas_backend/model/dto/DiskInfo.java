package com.nas_backend.model.dto;

// Represents single disk information
public record DiskInfo(
    String path, // E.g. "/mnt/nas/disk1"
    long totalSpaceMB, // Total space in MB
    long usableSpaceMB // Usable space in MB
) {}
