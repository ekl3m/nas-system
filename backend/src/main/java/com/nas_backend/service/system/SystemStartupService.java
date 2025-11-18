package com.nas_backend.service.system;

import com.nas_backend.model.config.AppConfig;
import com.nas_backend.service.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;

@Service
public class SystemStartupService {

    private static final Logger logger = LoggerFactory.getLogger(SystemStartupService.class);
    private static final String MARKER_FILENAME = ".clean_shutdown";

    private final EmailService emailService;
    private final AppConfigService configService;

    public SystemStartupService(EmailService emailService, AppConfigService configService) {
        this.emailService = emailService;
        this.configService = configService;
    }

    // Service methods

    // This method will be called on application shutdown
    @EventListener(ContextClosedEvent.class)
    public void onGracefulShutdown() {
        logger.info("SYSTEM: Shutdown detected. Creating clean shutdown marker...");
        try {
            File marker = getMarkerFile();
            if (marker.createNewFile()) {
                logger.info("SYSTEM: Shutdown marker created successfully.");
            }
        } catch (IOException e) {
            logger.error("SYSTEM: Failed to create shutdown marker!", e);
        }
    }

    // This method will be called when the application is fully started
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        AppConfig config = configService.getConfig();
        String hostname = config.getServer().getHostname();
        String jvmVersion = ManagementFactory.getRuntimeMXBean().getVmVersion();

        File marker = getMarkerFile();
        boolean isCleanStart = marker.exists();

        if (isCleanStart) {
            // Scenario A: Marker file exists indicating a clean shutdown
            logger.info("SYSTEM: Clean restart detected (marker found).");
            emailService.sendSystemSuccessEmail(
                "System '" + hostname + "' is ONLINE.\n" +
                "Status: Clean Restart - manual operation or deploy action)\n" +
                "JVM: " + jvmVersion
            );

            // Clean up the marker
            if (!marker.delete()) {
                logger.warn("SYSTEM: Failed to delete shutdown marker after startup.");
            }
        } else {
            // Scenario B: No marker file was found indicating an unexpected shutdown
            logger.warn("SYSTEM: Unexpected shutdown detected (no marker found)!");
            emailService.sendSystemErrorEmail(
                "System '" + hostname + "' has recovered from an unexpected shutdown.\n" +
                "Reason: Power loss, system crash, or forced kill.\n" +
                "Please check the hardware and logs.",
                "System"
            );
        }
    }

    // Helper methods

    private File getMarkerFile() {
        String rootPath = System.getProperty("APP_ROOT_PATH");
        // Marker file is located in data directory
        return Paths.get(rootPath, "data", MARKER_FILENAME).toFile();
    }
}