package com.nas_backend.service.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nas_backend.model.config.AppConfig;
import com.nas_backend.service.AppConfigService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class BackupService {

    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);
    private static final String DATA_DIR_NAME = "data";

    private final AppConfigService configService;
    private final EmailService emailService;

    public BackupService(AppConfigService configService, EmailService emailService) {
        this.configService = configService;
        this.emailService = emailService;
    }


    @Async // Do this operation in a separate thread without blocking the main application
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void backupDatabase() {
        // Wait a bit, so that the main application releases its blockade
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String rootPath = System.getProperty("APP_ROOT_PATH");
        Path source = Paths.get(rootPath, DATA_DIR_NAME, "nas.db");

        if (!Files.exists(source))
            return; // Nothing to backup

        AppConfig config = configService.getConfig();
        List<String> storagePaths = config.getStorage().getPaths();
        if (storagePaths == null)
            return;

        logger.info("[ASYNC-BACKUP] Performing database backup to all storage drives...");
        int successCount = 0;
        int failCount = 0;

        for (String drive : storagePaths) {
            try {
                Path destination = Paths.get(drive, ".nas.db.backup");

                Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                logger.info("[ASYNC-BACKUP] Backup successful to: {}", drive);
                successCount++;
            } catch (IOException e) {
                logger.error("[ASYNC-BACKUP] Failed to backup database to drive: {}", drive, e);
                failCount++;
            }
        }

        if (failCount > 0) {
            emailService.sendSystemErrorEmail(
                "Database backup FAILED.\n" +
                "Successful copies: " + successCount + "\n" +
                "Failed copies: " + failCount + "\n" +
                "Check system logs for details.",
                "System"
            );
        }
    }
}