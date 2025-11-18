package com.nas_backend.service.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LogService {
    private static final Logger eventLogger = LoggerFactory.getLogger("NAS_EVENTS");
    private static final Logger transferLogger = LoggerFactory.getLogger("NAS_TRANSFERS");

    // Logs a general system event, such as startup, shutdown, errors, etc
    public void logSystemEvent(String message) {
        eventLogger.info(message);
    }

    // Logs a file transfer event, such as upload, download
    public void logTransfer(String username, String action, String resource, String details) {
        String logMessage = String.format("[%s] %s: %s (%s)",
                username,
                action.toUpperCase(),
                resource,
                details != null ? details : "");
        transferLogger.info(logMessage);
    }

    // Overload method for simpler transfers, no details
    public void logTransfer(String username, String action, String resource) {
        logTransfer(username, action, resource, "");
    }

    // Method to retrieve log file content
    public List<String> getLogFileContent(String logFileName) {
        try {
            // Get the log file path
            String rootPath = System.getProperty("APP_ROOT_PATH");
            Path logPath = Paths.get(rootPath, "logs", logFileName);

            if (Files.notExists(logPath)) {
                return Collections.singletonList("Log file not found: " + logFileName);
            }

            // Read and return all lines from the log file
            return Files.readAllLines(logPath);
        } catch (IOException e) {
            return Collections.singletonList("Error reading log file: " + e.getMessage());
        }
    }
}