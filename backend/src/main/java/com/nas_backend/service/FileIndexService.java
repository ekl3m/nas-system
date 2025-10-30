package com.nas_backend.service;

import com.nas_backend.model.FileNode;
import com.nas_backend.repository.FileNodeRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class FileIndexService {
    private static final Logger logger = LoggerFactory.getLogger(FileIndexService.class);
    private static final String DATA_DIR_NAME = "data";

    private final FileNodeRepository fileNodeRepository;
    private final AppConfigService configService;

    public FileIndexService(AppConfigService appConfigService, FileNodeRepository fileNodeRepository) {
        this.configService = appConfigService;
        this.fileNodeRepository = fileNodeRepository;
    }

    // If file node DB does not exist, run a backup search
    @PostConstruct
    private void loadIndexOnStartup() {
        String rootPath = System.getProperty("APP_ROOT_PATH");
        String dbPath = Paths.get(rootPath, DATA_DIR_NAME, "nas.db").toString();
        File dbFile = new File(dbPath);

        if (dbFile.exists() && dbFile.length() > 0) {
            // Scenario A: File node DB exists
            logger.info("Database found at {}. Starting application.", dbPath);
            logger.info("Found {} existing nodes in database.", fileNodeRepository.count());
            return;
        }

        // Scenario B: File node DB does not exist, look for a backup to restore it from
        logger.warn("Main database file not found or is empty! Attempting to restore from backup...");

        if (restoreFromBackup()) {
            logger.info("Successfully restored database from backup. Starting application.");
            logger.info("Found {} nodes in restored database.", fileNodeRepository.count());
        } else {
            // Scenario C: Neither file node DB nor backup files were found. What a pity.
            logger.error("FATAL: No database file and no backups found. Starting with a new, empty database. All logical file structures are lost.");
        }
    }

    private boolean restoreFromBackup() {
        List<String> storagePaths = configService.getConfig().getStorage().getPaths();
        if (storagePaths == null || storagePaths.isEmpty()) {
            return false;
        }

        String rootPath = System.getProperty("APP_ROOT_PATH");
        Path destination = Paths.get(rootPath, DATA_DIR_NAME, "nas.db");

        for (String drive : storagePaths) {
            Path backupPath = Paths.get(drive, ".system_backup", "nas.db.backup");
            if (Files.exists(backupPath)) {
                try {
                    logger.warn("Found valid backup at {}. Copying to main DB path...", backupPath);
                    // Copy file node DB backup to data folder
                    Files.copy(backupPath, destination);
                    return true;
                } catch (IOException e) {
                    logger.error("Failed to copy backup from {}", backupPath, e);
                }
            }
        }
        return false;
    }

    private synchronized void backupDatabase() {
        String rootPath = System.getProperty("APP_ROOT_PATH");
        Path source = Paths.get(rootPath, DATA_DIR_NAME, "nas.db");

        if (!Files.exists(source))
            return; // Nothing to backup

        List<String> storagePaths = configService.getConfig().getStorage().getPaths();
        if (storagePaths == null)
            return;

        logger.info("Performing database backup to all storage drives...");
        for (String drive : storagePaths) {
            try {
                Path backupDir = Paths.get(drive, ".system_backup");
                Files.createDirectories(backupDir);
                Path destination = backupDir.resolve("nas.db.backup");

                // Copy file node DB to storage drive, overwrite old backup
                Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                logger.error("Failed to backup database to drive: {}", drive, e);
            }
        }
    }

    // Saves or updates one node in file node DB
    public void addOrUpdateNode(FileNode node) {
        fileNodeRepository.save(node);
        backupDatabase();
    }

    // Removes a node from file node DB grounding on its logical path
    public void removeNode(String logicalPath) {
        // Find a node, if it exists - delete it
        fileNodeRepository.findByLogicalPath(logicalPath).ifPresent(node -> {
            fileNodeRepository.delete(node);
            logger.info("Removed node from index: {}", logicalPath);
            backupDatabase();
        });
    }

    // Get one node's metadata from file node DB
    public FileNode getNode(String logicalPath) {
        return fileNodeRepository.findByLogicalPath(logicalPath).orElse(null);
    }

    // Check whether a node using a given logical path exists
    public boolean nodeExists(String logicalPath) {
        return fileNodeRepository.existsByLogicalPath(logicalPath);
    }

    // List all files/directories located directly inside a given directory
    public List<FileNode> listFiles(String directoryLogicalPath) {
        if (directoryLogicalPath == null || directoryLogicalPath.isEmpty() || directoryLogicalPath.equals("/")) {
            directoryLogicalPath = "/";
        }
        return fileNodeRepository.findByParentPath(directoryLogicalPath);
    }
}