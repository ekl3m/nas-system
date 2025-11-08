package com.nas_backend.service;

import com.nas_backend.model.AppConfig;
import com.nas_backend.model.FileInfo;
import com.nas_backend.model.FileNode;
import com.nas_backend.repository.FileNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    private final AppConfigService configService;
    private final FileIndexService fileIndexService;
    private final FileNodeRepository fileNodeRepository;

    public FileService(AppConfigService appConfigService, FileIndexService fileIndexService, FileNodeRepository fileNodeRepository) {
        this.configService = appConfigService;
        this.fileIndexService = fileIndexService;
        this.fileNodeRepository = fileNodeRepository;
    }

    @Transactional
    public void uploadFile(String logicalParentPath, MultipartFile file) throws IOException { 
        logger.info("Upload request for '{}' in logical path '{}'", file.getOriginalFilename(), logicalParentPath);

        // Make sure that parent path exists in file node database
        createVirtualPath(logicalParentPath);

        AppConfig config = configService.getConfig();
        long fileSize = file.getSize();
        String originalFileName = file.getOriginalFilename();

        // Size validation
        if (fileSize > (long) config.getServer().getMaxUploadSizeMB() * 1024 * 1024) {
            throw new IOException("File size exceeds the maximum upload limit.");
        }

        // Find a unique filename in the target folder
        String finalFileName = getUniqueFileName(logicalParentPath, originalFileName);
        String finalLogicalPath = Paths.get(logicalParentPath, finalFileName).toString().replace("\\", "/");

        if (!finalFileName.equals(originalFileName)) {
            logger.warn("CONFLICT: Original name was taken. Saving as: {}", finalFileName);
        }

        // Find best storage path and save the file
        String bestStoragePath = findBestStoragePath(fileSize);
        String userName = logicalParentPath.split("/")[0];

        // Use a unique physical name (UUID) - this is still crucial
        String uniquePhysicalName = UUID.randomUUID().toString() + "-" + originalFileName;
        Path physicalPath = Paths.get(bestStoragePath, userName, uniquePhysicalName);

        Files.createDirectories(physicalPath.getParent());
        file.transferTo(physicalPath);
        logger.info("File saved successfully to new physical path: {}", physicalPath);

        // ALWAYS create a new node
        FileNode nodeToSave = new FileNode();
        nodeToSave.setCreatedAt(Instant.now());

        // Set all fields
        nodeToSave.setLogicalPath(finalLogicalPath);
        nodeToSave.setParentPath(logicalParentPath);
        nodeToSave.setPhysicalPath(physicalPath.toString());
        nodeToSave.setFileName(finalFileName);
        nodeToSave.setDirectory(false);
        nodeToSave.setSize(fileSize);
        nodeToSave.setModifiedAt(Instant.now());
        nodeToSave.setRestorePath(null); // Always null on a new upload

        fileIndexService.addOrUpdateNode(nodeToSave);
        logger.info("File index (DB) updated for logical path: {}", finalLogicalPath);
    }

    public Resource getResource(String logicalPath) throws IOException {
        logger.info("Resource request for logical path: {}", logicalPath);

        // Ask file node DB about the node
        FileNode node = fileIndexService.getNode(logicalPath);
        if (node == null) {
            throw new IOException("File not found in index: " + logicalPath);
        }

        // Verify whether it is a file or a directory
        if (node.isDirectory()) {
            // It is a directory, zip it and return as a file
            return getFolderAsZip(logicalPath);
        } else {
            // It is a file, return it from its physical path
            File file = new File(node.getPhysicalPath());
            if (!file.exists()) {
                logger.error("File inconsistency! Found in DB but not on disk: {}", node.getPhysicalPath());
                fileIndexService.removeNode(logicalPath);
                throw new IOException("File not found on disk, index corrected.");
            }
            return new FileSystemResource(file);
        }
    }

    @Transactional
    public void deleteResource(String logicalPath) throws IOException {
        AppConfig config = configService.getConfig();
        logger.warn("Delete request for logical path: {}", logicalPath);

        if (config.getTrashCan().isEnabled()) {
            FileNode rootNodeToDelete = fileIndexService.getNode(logicalPath);
            if (rootNodeToDelete == null) {
                throw new IOException("Resource to delete not found in index: " + logicalPath);
            }

            String userName = rootNodeToDelete.getParentPath().split("/")[0];
            String trashParentPath = userName + "/trash";

            // Find a unique name in the trash
            String newFileNameInTrash = getUniqueFileName(trashParentPath, rootNodeToDelete.getFileName());
            String newLogicalPathInTrash = Paths.get(trashParentPath, newFileNameInTrash).toString().replace("\\", "/");

            // Call the universal move engine
            logger.info("Moving resource to trash as: {}", newLogicalPathInTrash);
            moveResource(logicalPath, newLogicalPathInTrash);

        } else {
            // Permanent deletion
            logger.warn("Trash can is disabled. Permanently deleting resources.");
            deleteRecursively(logicalPath);
        }
    }

    @Transactional
    public void restoreResource(String logicalPathInTrash) throws IOException {
        logger.info("Restore request for resource: {}", logicalPathInTrash);

        // Find the node and its restore path
        FileNode rootNodeToRestore = fileNodeRepository.findByLogicalPath(logicalPathInTrash).orElse(null);
        if (rootNodeToRestore == null) {
            throw new IOException("Resource to restore not found in trash: " + logicalPathInTrash);
        }

        String restoreParentPath = rootNodeToRestore.getRestorePath();
        if (restoreParentPath == null) {
            throw new IOException("Cannot restore: This item is not a valid trash item (restorePath is null).");
        }

        // Calculate the original target path
        String targetLogicalPath = Paths.get(restoreParentPath, rootNodeToRestore.getFileName()).toString()
                .replace("\\", "/");

        // Call the universal move engine
        logger.info("Restoring resource from {} to {}", logicalPathInTrash, targetLogicalPath);
        moveResource(logicalPathInTrash, targetLogicalPath);
    }

    @Transactional
    public void moveResource(String oldLogicalPath, String newLogicalPath) throws IOException {
        logger.info("Universal Smart Move: from [{}] to [{}]", oldLogicalPath, newLogicalPath);

        // Validate source
        FileNode rootNodeToMove = fileNodeRepository.findByLogicalPath(oldLogicalPath).orElse(null);
        if (rootNodeToMove == null) {
            throw new IOException("Source resource not found in index: " + oldLogicalPath);
        }

        String originalParentPath = rootNodeToMove.getParentPath();

        // Handle destination conflicts
        String finalLogicalPath = newLogicalPath;
        String finalFileName = Paths.get(newLogicalPath).getFileName().toString();
        String targetParentPath = Paths.get(newLogicalPath).getParent().toString().replace("\\", "/");

        if (fileNodeRepository.existsByLogicalPath(newLogicalPath)) {
            logger.warn("CONFLICT: Destination {} exists. Finding unique name...", newLogicalPath);
            finalFileName = getUniqueFileName(targetParentPath, finalFileName);
            finalLogicalPath = Paths.get(targetParentPath, finalFileName).toString().replace("\\", "/");
            logger.warn("CONFLICT RESOLVED: Renaming moved resource to: {}", finalLogicalPath);
        }

        // Get all nodes to move
        List<FileNode> nodesToMove = fileNodeRepository.findByLogicalPathStartingWith(oldLogicalPath);
        logger.info("Moving {} nodes...", nodesToMove.size());

        for (FileNode node : nodesToMove) {
            String oldSubPath = node.getLogicalPath().substring(oldLogicalPath.length());
            String calculatedNewLogicalPath = finalLogicalPath + oldSubPath;
            String calculatedNewParentPath = Paths.get(calculatedNewLogicalPath).getParent().toString().replace("\\",
                    "/");

            // "Move" it virtually
            node.setLogicalPath(calculatedNewLogicalPath);
            node.setParentPath(calculatedNewParentPath);
            node.setModifiedAt(Instant.now());

            // Set filename and restore path (only for the root node of the move)
            if (node.getLogicalPath().equals(calculatedNewLogicalPath)) {
                node.setFileName(finalFileName);
                node.setRestorePath(originalParentPath); // Save original parent path
            }

            fileIndexService.addOrUpdateNode(node);
        }

        logger.info("Move complete. Root node '{}' is now at '{}'", oldLogicalPath, finalLogicalPath);
    }

    public List<FileInfo> listFiles(String logicalPath) {
        logger.info("List files request for logical path: {}", logicalPath);
        List<FileNode> nodes = fileIndexService.listFiles(logicalPath);
        
        // Map FileNode to FileInfo
        return nodes.stream()
                .map(this::toFileInfo)
                .collect(Collectors.toList());
    }

    @Transactional
    public void createVirtualPath(String logicalPath) {
        // Check whether this directory exists in file node database
        if (fileNodeRepository.existsByLogicalPath(logicalPath)) {
            return; // Exists, job done
        }

        // Stop condition for recurrency. If main user folder is reached, return
        Path path = Paths.get(logicalPath);
        if (path.getNameCount() <= 1) {
            return;
        }

        // Parent folder does not exist, it needs to be created
        String parentPathStr = path.getParent().toString().replace("\\", "/");
        createVirtualPath(parentPathStr); // <--- Recurrency

        logger.info("Auto-creating virtual folder in DB: {}", logicalPath);

        FileNode folderNode = new FileNode();
        folderNode.setLogicalPath(logicalPath);
        folderNode.setParentPath(parentPathStr);
        folderNode.setFileName(path.getFileName().toString());
        folderNode.setDirectory(true);
        folderNode.setPhysicalPath("virtual"); // Directories do not have physical path
        folderNode.setSize(0);
        folderNode.setCreatedAt(Instant.now());
        folderNode.setModifiedAt(Instant.now());
        folderNode.setRestorePath(null);

        fileIndexService.addOrUpdateNode(folderNode);
    }

    private String getUniqueFileName(String targetParentPath, String originalFileName) {
        // Check if the original path is available
        String finalLogicalPath = Paths.get(targetParentPath, originalFileName).toString().replace("\\", "/");
        if (!fileIndexService.nodeExists(finalLogicalPath)) {
            return originalFileName; // It's available, do nothing
        }

        // Path is taken. Time for "Windows 95" logic.
        String baseName;
        String extension;
        int dotIndex = originalFileName.lastIndexOf('.');

        if (dotIndex > 0) {
            baseName = originalFileName.substring(0, dotIndex);
            extension = originalFileName.substring(dotIndex);
        } else {
            baseName = originalFileName;
            extension = "";
        }

        int count = 1;
        String newFileName;
        do {
            newFileName = baseName + "(" + count + ")" + extension;
            finalLogicalPath = Paths.get(targetParentPath, newFileName).toString().replace("\\", "/");
            count++;
        } while (fileIndexService.nodeExists(finalLogicalPath));

        return newFileName;
    }

    @Transactional
    protected void deleteRecursively(String logicalPath) throws IOException {
        List<FileNode> nodesToDelete = fileNodeRepository.findByLogicalPathStartingWith(logicalPath);

        for (FileNode node : nodesToDelete) {
            // Delete physical file (only if it is a file, directories do not exist physically)
            if (!node.isDirectory()) {
                File file = new File(node.getPhysicalPath());
                if (file.exists()) {
                    Files.delete(file.toPath());
                } else {
                    logger.warn("Tried to delete physical file, but it was already gone: {}", node.getPhysicalPath());
                }
            }
            // Delete file node DB entry
            fileNodeRepository.delete(node);
        }
        logger.info("Permanently deleted {} nodes starting with logical path: {}", nodesToDelete.size(), logicalPath);
    }

    private Resource getFolderAsZip(String logicalPath) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

            // Find all children nodes (files and directories)
            List<FileNode> children = fileNodeRepository.findByLogicalPathStartingWith(logicalPath + "/");

            for (FileNode node : children) {
                // Create a relative path inside ZIP file
                String zipEntryName = node.getLogicalPath().substring(logicalPath.length() + 1);

                if (node.isDirectory()) {
                    // Scenario A: It is a directory
                    ZipEntry zipEntry = new ZipEntry(zipEntryName + "/");
                    zos.putNextEntry(zipEntry);
                    zos.closeEntry();
                } else {
                    // Scenario B: It is a file
                    File file = new File(node.getPhysicalPath());
                    if (!file.exists()) continue; // Skip, if file is not physically there

                    try (FileInputStream fis = new FileInputStream(file)) {
                        ZipEntry zipEntry = new ZipEntry(zipEntryName);
                        zos.putNextEntry(zipEntry);
                        byte[] bytes = new byte[1024];
                        int length;
                        while ((length = fis.read(bytes)) >= 0) {
                            zos.write(bytes, 0, length);
                        }
                        zos.closeEntry();
                    }
                }
            }
        }
        return new ByteArrayResource(baos.toByteArray());
    }

    // Mapper FileNode -> FileInfo (DTO)
    private FileInfo toFileInfo(FileNode node) {
        return new FileInfo(
                node.getFileName(),
                node.isDirectory(),
                node.getSize(),
                node.getModifiedAt().toString(),
                node.getParentPath()
        );
    }

    private String findBestStoragePath(long requiredSpace) throws IOException {
        List<String> paths = configService.getConfig().getStorage().getPaths();
        if (paths.isEmpty())
            throw new IOException("No storage paths configured!");
        String bestPath = null;
        long maxFreeSpace = -1;
        for (String pathStr : paths) {
            Path path = Paths.get(pathStr);
            if (Files.notExists(path)) Files.createDirectories(path);
            FileStore store = Files.getFileStore(path);
            long usableSpace = store.getUsableSpace();
            if (usableSpace > maxFreeSpace) {
                maxFreeSpace = usableSpace;
                bestPath = pathStr;
            }
        }
        if (bestPath == null || maxFreeSpace < requiredSpace) {
            throw new IOException("Not enough space on any storage device.");
        }
        return bestPath;
    }
}