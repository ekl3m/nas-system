package com.nas_backend.service.monitor;

import com.nas_backend.model.DiskInfo;
import java.util.List;

// Interface for system monitoring functionalities
public interface SystemMonitor {
    
    // Returns list of mounted disks' information
    List<DiskInfo> getDiskInfo();

    // Returns CPU temperature in degrees Celsius
    double getCpuTemperature();

    // Returns used RAM in megabytes
    long getUsedMemoryMB();

    // Returns total RAM in megabytes
    long getTotalMemoryMB();
}
