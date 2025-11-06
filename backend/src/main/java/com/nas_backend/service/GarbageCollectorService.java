package com.nas_backend.service;

import com.nas_backend.model.AppConfig;
import com.nas_backend.model.FileNode;
import com.nas_backend.repository.FileNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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

    @Scheduled(cron = "0 0 3 * * ?")// AM
    @Transactional
    public void emptyTrashTask() {
        AppConfig config = configService.getConfig();
        if (!config.getTrashCan().isEnabled()) {
            logger.info("Garbage Collector: Trash is disabled, skipping task.");
            return;
        }

        int retentionDays = config.getTrashCan().getRetentionDays();
        logger.info("Garbage Collector: Running task to clean items older than {} days...", retentionDays);

        // Find expired roots in trash
        Instant cutoffDate = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        List<FileNode> expiredRoots = fileNodeRepository.findByRestorePathIsNotNullAndModifiedAtBefore(cutoffDate);

        if (expiredRoots.isEmpty()) {
            logger.info("Garbage Collector: No expired root items found in trash. Job done.");
            return;
        }

        int totalDeletedFiles = 0;
        int totalDeletedNodes = 0;

        logger.info("Garbage Collector: Found {} expired root items to delete.", expiredRoots.size());

        // For each root, delete its branches
        for (FileNode root : expiredRoots) {
            String rootLogicalPath = root.getLogicalPath();

            // Find nodes to delete 
            List<FileNode> nodesToDelete = fileNodeRepository.findByLogicalPathStartingWith(rootLogicalPath);

            int deletedFiles = 0;
            int deletedNodes = 0;

            for (FileNode node : nodesToDelete) {
                // Delete physical file if it is not a directory (which are not physical)
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
}