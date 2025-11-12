package com.nas_backend.service.system.monitor;

import com.nas_backend.model.config.AppConfig;
import com.nas_backend.model.dto.DiskInfo;
import com.nas_backend.service.AppConfigService;
import com.nas_backend.service.system.ShellService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

// Implementation of SystemMonitor for Raspberry Pi systems
public class RaspberrySystemMonitor implements SystemMonitor {

    private static final Logger logger = LoggerFactory.getLogger(RaspberrySystemMonitor.class);
    private final AppConfigService configService;
    private final ShellService shellService;

    public RaspberrySystemMonitor(AppConfigService configService, ShellService shellService) {
        this.configService = configService;
        this.shellService = shellService;
    }

    // Utilizes Java NIO to gather disk information
    @Override
    public List<DiskInfo> getDiskInfo() {
        AppConfig config = configService.getConfig();
        List<String> paths = config.getStorage().getPaths();
        List<DiskInfo> disks = new ArrayList<>();

        if (paths == null) {
            logger.warn("No storage paths configured.");
            return disks;
        }

        for (String pathStr : paths) {
            try {
                Path path = Paths.get(pathStr);
                FileStore store = Files.getFileStore(path);

                // Get real disk stats
                long realTotalMB = store.getTotalSpace() / (1024 * 1024);
                long realUsableMB = store.getUsableSpace() / (1024 * 1024);

                // Append to the list
                disks.add(new DiskInfo(pathStr, realTotalMB, realUsableMB));

            } catch (IOException e) {
                logger.error("Failed to get disk info for path: {}", pathStr, e);
                disks.add(new DiskInfo(pathStr, 0, 0));
            }
        }
        return disks;
    }

    // Executes the 'vcgencmd' command specific to Raspberry Pi
    @Override
    public double getCpuTemperature() {
        String result = shellService.executeCommand("vcgencmd measure_temp"); // Returns e.g. "temp=54.3'C"
        if (result == null || !result.contains("=")) {
            return -1.0; // Error
        }
        try {
            return Double.parseDouble(result.split("=")[1].split("'")[0]);
        } catch (Exception e) {
            logger.error("Failed to parse CPU temp from: {}", result, e);
            return -1.0;
        }
    }

    // Executes the 'free -m' command specific to Linux
    @Override
    public long getUsedMemoryMB() {
        String result = shellService.executeCommand("free -m");
        /* 
        The output looks roughly like this:
        total used free shared buff/cache available
        Mem: 3726 417 2217 146 1091 3000
        Swap: 0 0 0 
        */
        if (result == null)
            return -1;

        try {
            String[] lines = result.split("\n");
            for (String line : lines) {
                if (line.startsWith("Mem:")) {
                    String[] parts = line.split("\\s+"); // Split by whitespace
                    return Long.parseLong(parts[2]); // Column "used"
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse 'used' memory from 'free -m' output", e);
        }
        return -1;
    }

    // Uses the same 'free -m' command to get total memory
    @Override
    public long getTotalMemoryMB() {
        String result = shellService.executeCommand("free -m");
        if (result == null)
            return -1;

        try {
            String[] lines = result.split("\n");
            for (String line : lines) {
                if (line.startsWith("Mem:")) {
                    String[] parts = line.split("\\s+");
                    return Long.parseLong(parts[1]); // Column "total"
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse 'total' memory from 'free -m' output", e);
        }
        return -1;
    }   
}