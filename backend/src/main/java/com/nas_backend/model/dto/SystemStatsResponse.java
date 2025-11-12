package com.nas_backend.model.dto;

import java.util.List;

// Represents system statistics response
public record SystemStatsResponse(
    // Storage statistics
    List<DiskInfo> disks,
    long totalSpaceMB,
    long totalUsableSpaceMB,

    // System statistics (CPU/RAM)
    double cpuTemperature, // In degrees Celsius
    long usedMemoryMB,
    long totalMemoryMB
) {}
