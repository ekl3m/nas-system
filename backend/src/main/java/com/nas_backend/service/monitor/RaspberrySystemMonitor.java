package com.nas_backend.service.monitor;

import com.nas_backend.model.AppConfig;
import com.nas_backend.model.DiskInfo;
import com.nas_backend.service.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

    public RaspberrySystemMonitor(AppConfigService configService) {
        this.configService = configService;
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
        String result = executeCommand("vcgencmd measure_temp"); // Returns e.g. "temp=54.3'C"
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
        String result = executeCommand("free -m");
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
        String result = executeCommand("free -m");
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

    // Helper method to execute shell commands and return output as String
    private String executeCommand(String command) {
        try {
            // Split command into parts for ProcessBuilder
            String[] commandParts = command.split(" ");

            // Use the robust ProcessBuilder
            ProcessBuilder pb = new ProcessBuilder(commandParts);
            pb.redirectErrorStream(true); // Combine error stream (stderr) with output (stdout)
            Process process = pb.start(); // Start the process

            StringBuilder output = new StringBuilder();

            // Try-with-resources automatically closes the reader
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;

                // Read all output lines
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // Wait for the command to actually finish and get its exit code
            int exitVal = process.waitFor();

            // Check the exit code. '0' is the universal sign for "SUCCESS" in Linux/Unix  
            if (exitVal == 0) {
                return output.toString().trim();
            } else {
                // If the exit code is anything else (e.g., 1, 127), it means the command failed
                logger.warn("Command '{}' failed with exit code: {}", command, exitVal);
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to execute command: {}", command, e);
            return null;
        }
    }
}
