package com.nas_backend.service.system.monitor;

import com.nas_backend.model.config.AppConfig;
import com.nas_backend.model.dto.DiskInfo;
import com.nas_backend.service.AppConfigService;

import java.util.List;

// Mock implementation of SystemMonitor for development/testing purposes
public class MockSystemMonitor implements SystemMonitor {

    private final AppConfigService configService;

    public MockSystemMonitor(AppConfigService configService) {
        this.configService = configService;
    }

    @Override
    public List<DiskInfo> getDiskInfo() {
        AppConfig config = configService.getConfig();
        List<String> paths = config.getStorage().getPaths();

        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        
        // Return mock data with configured paths
        return List.of(new DiskInfo(paths.get(0), 1000 * 1024, 990 * 1024));
    }

    @Override
    public double getCpuTemperature() {
        return 63.0; // Example temperature
    }

    @Override
    public long getUsedMemoryMB() {
        return 2048; // Example used RAM
    }

    @Override
    public long getTotalMemoryMB() {
        return 8192; // 8GB RAM
    }
}
