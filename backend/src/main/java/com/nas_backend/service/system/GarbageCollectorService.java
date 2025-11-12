package com.nas_backend.service.system;

import com.nas_backend.model.config.AppConfig;
import com.nas_backend.model.entity.FileNode;
import com.nas_backend.repository.FileNodeRepository;
import com.nas_backend.service.AppConfigService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class GarbageCollectorService {

    private static final Logger logger = LoggerFactory.getLogger(GarbageCollectorService.class);

    private final FileNodeRepository fileNodeRepository;
    private final AppConfigService configService;

    public GarbageCollectorService(FileNodeRepository fileNodeRepository, AppConfigService configService) {
        this.fileNodeRepository = fileNodeRepository;
        this.configService = configService;
    }

    @Scheduled(cron = "0 0 3 * * ?") // Runs at 3:00 AM daily
    @Transactional
    public void emptyTrashTask() {
        AppConfig config = configService.getConfig();
        if (!config.getTrashCan().isEnabled()) {
            logger.info("Garbage Collector: Trash can is disabled, skipping task.");
            return;
        }

        int retentionDays = config.getTrashCan().getRetentionDays();
        logger.info("Garbage Collector: Running task to clean items older than {} days...", retentionDays);

        // Find expired roots in trash
        Instant cutoffDate = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        List<FileNode> expiredRoots = fileNodeRepository
                .findByParentPathEndingWithAndModifiedAtBefore("/trash", cutoffDate);

        if (expiredRoots.isEmpty()) {
            logger.info("Garbage Collector: No expired root items found in trash. Job done.");
            return;
        }

        int totalDeletedFiles = 0;
        int totalDeletedNodes = 0;

        logger.info("Garbage Collector: Found {} expired root items to delete.", expiredRoots.size());

        // For each root, delete its branches (This logic was already perfect)
        for (FileNode root : expiredRoots) {
            String rootLogicalPath = root.getLogicalPath();

            // Find nodes to delete (the root AND all its children)
            List<FileNode> nodesToDelete = fileNodeRepository.findByLogicalPathStartingWith(rootLogicalPath);

            int deletedFiles = 0;
            int deletedNodes = 0;

            for (FileNode node : nodesToDelete) {
                // Delete physical file if it is not a directory
                if (!node.isDirectory()) {
                    File file = new File(node.getPhysicalPath());
                    if (file.exists()) {
                        try {
                            Files.delete(file.toPath());
                            deletedFiles++;
                        } catch (IOException e) {
                            logger.error("Garbage Collector: Failed to delete physical file: {}",
                                    node.getPhysicalPath(), e);
                        }
                    }
                }
                // Delete file node DB entry
                fileNodeRepository.delete(node);
                deletedNodes++;
            }

            logger.info("Garbage Collector: Deleted item '{}' ({} nodes, {} physical files).", root.getFileName(),
                    deletedNodes, deletedFiles);
            totalDeletedFiles += deletedFiles;
            totalDeletedNodes += deletedNodes;
        }

        logger.info("Garbage Collector: Task finished. Permanently deleted {} total nodes and {} total physical files.",
                totalDeletedNodes, totalDeletedFiles);
    }

    @Scheduled(cron = "0 5 3 * * ?") // Runs at 3:05 AM daily
    @Transactional
    public void cleanOrphanEntriesTask() {
        logger.info("Garbage Collector (Orphans): Running task to find and remove orphan DB entries...");
        
        // Get all active files from the database (select files that are not directories and are not in the trash)
        List<FileNode> allActiveFiles = fileNodeRepository.findAllActiveFiles("/trash");

        if (allActiveFiles.isEmpty()) {
            logger.info("Garbage Collector (Orphans): No active files found to check. Job done.");
            return;
        }
        
        int orphanCount = 0;
        
        // Check each file for physical existence
        for (FileNode node : allActiveFiles) {
            Path physicalPath = Paths.get(node.getPhysicalPath());
            
            if (Files.notExists(physicalPath)) {
                // This is an orphan entry! The file is in the DB but not on the disk
                logger.warn("Garbage Collector (Orphans): Found orphan entry! Physical file missing: {}", node.getPhysicalPath());
                fileNodeRepository.delete(node);
                orphanCount++;
            }
        }

        logger.info("Garbage Collector (Orphans): Task finished. Found and removed {} orphan entries.", orphanCount);
    }
}