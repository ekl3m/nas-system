package com.nas_backend.service.file;

import com.nas_backend.model.entity.FileNode;
import com.nas_backend.repository.FileNodeRepository;
import com.nas_backend.service.AppConfigService;
import com.nas_backend.service.system.BackupService;
import com.nas_backend.service.system.LogService;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.List;

@Service
public class FileIndexService {
    private static final Logger logger = LoggerFactory.getLogger(FileIndexService.class);
    private static final String DATA_DIR_NAME = "data";

    private final FileNodeRepository fileNodeRepository;
    private final AppConfigService configService;
    private final BackupService backupService;
    private final LogService logService;

    public FileIndexService(AppConfigService appConfigService, FileNodeRepository fileNodeRepository, BackupService backupService, LogService logService) {
        this.configService = appConfigService;
        this.fileNodeRepository = fileNodeRepository;
        this.backupService = backupService;
        this.logService = logService;
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
            String msg = "CRITICAL: Main database was missing/corrupt. System successfully RESTORED from backup.";
            logger.info(msg);
            logService.logSystemEvent(msg);
            logger.info("Found {} nodes in restored database.", fileNodeRepository.count());
        } else {
            // Scenario C: Neither file node DB nor backup files were found. What a pity!
            String msg = "FATAL: No database and no backups found. Starting with EMPTY database. Logical structure lost.";
            logger.error(msg);
            logService.logSystemEvent(msg);
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
            Path backupPath = Paths.get(drive, ".nas.db.backup");

            if (Files.exists(backupPath)) {
                try {
                    logger.warn("Found valid backup at {}. Copying to main DB path...", backupPath);
                    Files.copy(backupPath, destination);
                    return true;
                } catch (IOException e) {
                    logger.error("Failed to copy backup from {}", backupPath, e);
                }
            }
        }
        return false;
    }

    // Saves or updates one node in file node database and performs a backup
    public FileNode addOrUpdateNode(FileNode node) {
        // Node cannot be null
        Objects.requireNonNull(node, "FileNode to be saved cannot be null");

        FileNode savedNode = fileNodeRepository.save(node);
        backupService.backupDatabase();
        return savedNode;
    }

    // Saves a list of nodes in one transaction and performs one backup
    @Transactional
    public List<FileNode> addOrUpdateNodes(List<FileNode> nodes) {
        // List of nodes cannot be null
        Objects.requireNonNull(nodes, "List of FileNodes to be saved cannot be null");

        List<FileNode> savedNodes = fileNodeRepository.saveAll(nodes);
        backupService.backupDatabase();
        return savedNodes;
    }

    // Removes a node from file node DB grounding on its logical path
    public void removeNode(String logicalPath) {
        // Find a node, if it exists - delete it
        fileNodeRepository.findByLogicalPath(logicalPath).ifPresent(node -> {
            fileNodeRepository.delete(node);
            logger.info("Removed node from index: {}", logicalPath);
            backupService.backupDatabase();
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