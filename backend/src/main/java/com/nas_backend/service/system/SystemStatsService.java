package com.nas_backend.service.system;

import com.nas_backend.model.config.AppConfig;
import com.nas_backend.model.dto.DiskInfo;
import com.nas_backend.model.dto.SystemStatsResponse;
import com.nas_backend.service.AppConfigService;
import com.nas_backend.service.system.monitor.SystemMonitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SystemStatsService {

    private static final Logger logger = LoggerFactory.getLogger(SystemStatsService.class);

    // Data collector interface
    private final SystemMonitor monitor;
    private final AppConfigService configService;

    public SystemStatsService(SystemMonitor monitor, AppConfigService configService) {
        this.monitor = monitor;
        this.configService = configService;
    }

    // Gathers and compiles system statistics, while applying config constraints
    public SystemStatsResponse getSystemStats() {
        // Collect data from the monitor
        List<DiskInfo> disks = monitor.getDiskInfo();
        double temp = monitor.getCpuTemperature();
        long usedMem = monitor.getUsedMemoryMB();
        long totalMem = monitor.getTotalMemoryMB();

        // Do some quick math (sum up disks)
        long realTotalMB = disks.stream()
                .mapToLong(DiskInfo::totalSpaceMB)
                .sum();
        long realUsableMB = disks.stream()
                .mapToLong(DiskInfo::usableSpaceMB)
                .sum();
        long realUsedMB = realTotalMB - realUsableMB;

        // Get quota from config (in MB)
        AppConfig config = configService.getConfig();
        long quotaMB = 0;
        if (config.getStorage() != null) {
            quotaMB = (long) config.getStorage().getQuotaGB() * 1024;
        }

        // Calculate effective values to show to the user
        long effectiveTotalMB;
        long effectiveUsableMB;

        if (quotaMB > 0) {
            // User set a limit. Check if it's smaller than the physical size
            effectiveTotalMB = Math.min(realTotalMB, quotaMB);
        } else {
            // User did not set a limit, use real size
            effectiveTotalMB = realTotalMB;
        }

        // Calculate effective free space based on the effective ceiling
        effectiveUsableMB = effectiveTotalMB - realUsedMB;
        // Safety check - never show negative free space
        if (effectiveUsableMB < 0) {
            effectiveUsableMB = 0;
        }

        logger.info("System Stats: Real Total={}MB, Quota={}MB -> Effective Total={}MB",
                realTotalMB, quotaMB, effectiveTotalMB);

        // Build and return the final, robust report
        return new SystemStatsResponse(disks, effectiveTotalMB, effectiveUsableMB, temp, usedMem, totalMem);
    }
}
