package com.nas_backend.service.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final LogService logService;
    private final ShellService shellService;
    private final StorageMetricsService storageMetricsService;

    public BackupService(AppConfigService configService, EmailService emailService, LogService logService, ShellService shellService,
                         StorageMetricsService storageMetricsService) {
        this.configService = configService;
        this.emailService = emailService;
        this.logService = logService;
        this.shellService = shellService;
        this.storageMetricsService = storageMetricsService;
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

        logger.info("Database Backup: Performing database backup to all storage drives...");
        int successCount = 0;
        int failCount = 0;

        for (String drive : storagePaths) {
            try {
                Path destination = Paths.get(drive, ".nas.db.backup");
                Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                logger.info("Database Backup: Backup successful to: {}", drive);
                logService.logSystemEvent("Database Backup created successfully at: " + destination);

                successCount++;
            } catch (IOException e) {
                logger.error("Database Backup: Failed to backup database to drive: {}", drive, e);
                logService.logSystemEvent("CRITICAL: Database Backup failed for drive: " + drive);
                
                failCount++;
            }
        }

        if (failCount > 0) {
            emailService.sendSystemErrorEmail(
                "Database backup failed.\n" +
                "Successful copies: " + successCount + "\n" +
                "Failed copies: " + failCount + "\n" +
                "Check system logs for details.",
                "System"
            );
        }
    }

    // Main backup method, scheduled to run daily
    @Scheduled(cron = "0 0 4 * * ?") // 4:00 AM daily
    @Async
    public void backupFiles() {
        AppConfig config = configService.getConfig();
        if (!config.getBackup().isEnabled()) {
            logger.info("File Backup: Disabled in config. Skipping.");
            return;
        }

        List<String> sources = config.getStorage().getPaths();
        List<String> destinations = config.getBackup().getPaths();

        if (sources == null || sources.isEmpty() || destinations == null || destinations.isEmpty()) {
            logger.warn("File Backup: No source or destination paths configured.");
            return;
        }

        // Verify that we have matching counts
        if (sources.size() != destinations.size()) {
            String errMsg = "File Backup: Storage paths count does not match Backup paths count. Check config.";
            logger.error(errMsg);
            logService.logSystemEvent("CRITICAL: " + errMsg);
            emailService.sendSystemErrorEmail(errMsg, "System Backup - Configuration Error");
            return;
        }

        long quotaGB = config.getBackup().getQuotaGB();
        if (quotaGB > 0) { // Check quota only if it is set
            logger.info("File Backup: Checking quota limits (Limit: {} GB)...", quotaGB);
            long totalSourceSizeByte = 0;

            // Counting the size of all source folders
            for (String sourcePath : sources) {
                try {
                    totalSourceSizeByte += storageMetricsService.calculateDirectorySize(Paths.get(sourcePath));
                } catch (IOException e) {
                    logger.error("File Backup: Failed to calculate size for path: {}", sourcePath, e);
                    // In case of read error, better to abort than risk
                    return;
                }
            }

            long totalSourceSizeGB = totalSourceSizeByte / (1024 * 1024 * 1024);
            logger.info("File Backup: Current source size: {} bytes (~{} GB)", totalSourceSizeByte, totalSourceSizeGB);

            if (totalSourceSizeGB > quotaGB) {
                String msg = String.format("File Backup ABORTED: Total source size (%d GB) exceeds defined backup quota (%d GB).", totalSourceSizeGB, quotaGB);
                logger.error(msg);
                logService.logSystemEvent("CRITICAL: " + msg);
                emailService.sendSystemErrorEmail(msg + "\nIncrease quota in config.json or clean up files.", "System Backup - Quota Exceeded");
                return; // Abort backup
            }
        }

        logger.info("File Backup: Starting rsync job for {} pairs...", sources.size());
        logService.logSystemEvent("File backup started for all configured storage pairs.");

        int errors = 0;

        // 1:1 loop based on index
        for (int i = 0; i < sources.size(); i++) {
            String source = sources.get(i);
            String dest = destinations.get(i);

            try {
                // Build rsync command
                // -a: archive mode (preserves permissions, dates)
                // -v: verbose
                // --delete: deletes files in backup that are not in source (full mirror)
                String command = String.format("rsync -av --delete %s/ %s/", source, dest);

                logger.info("Executing: {}", command);
                String result = shellService.executeCommand(command);

                if (result != null) {
                    logger.info("Backup successful from {} to {}", source, dest);
                    logService.logSystemEvent(String.format("File backup successful from %s to %s.", source, dest));
                } else {
                    errors++;
                    logger.error("Backup failed from {} to {}", source, dest);
                    logService.logSystemEvent(String.format("ERROR: File backup failed from %s to %s.", source, dest));
                }
            } catch (Exception e) {
                errors++;
                logger.error("Critical error during backup loop for source {}", source, e);
                logService.logSystemEvent(String.format("CRITICAL: Error during backup from %s. Check logs.", source));
            }
        }

        if (errors == 0) {
            logService.logSystemEvent("All file backups completed successfully.");
        } else {
            String msg = "File backup finished with " + errors + " errors. Check system logs.";
            logService.logSystemEvent("CRITICAL: " + msg);
            emailService.sendSystemErrorEmail(msg, "System Backup - Errors");
        }
    }
}