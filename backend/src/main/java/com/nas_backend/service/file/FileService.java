package com.nas_backend.service.file;

import com.nas_backend.exception.FileValidationException;
import com.nas_backend.model.config.AppConfig;
import com.nas_backend.model.dto.FileInfo;
import com.nas_backend.model.dto.FileOperationResponse;
import com.nas_backend.model.entity.FileNode;
import com.nas_backend.repository.FileNodeRepository;
import com.nas_backend.service.AppConfigService;
import com.nas_backend.service.system.LogService;
import com.nas_backend.service.system.StorageMetricsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
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
    private final LogService logService;
    private final StorageMetricsService storageMetricsService;
    private final FileNodeRepository fileNodeRepository;

    public FileService(AppConfigService appConfigService, FileIndexService fileIndexService, LogService logService, StorageMetricsService storageMetricsService,
                       FileNodeRepository fileNodeRepository) {
        this.configService = appConfigService;
        this.fileIndexService = fileIndexService;
        this.logService = logService;
        this.storageMetricsService = storageMetricsService;
        this.fileNodeRepository = fileNodeRepository;
    }

    // Main methods (engines)

    @Transactional
    public FileOperationResponse uploadFile(String logicalParentPath, MultipartFile file) throws IOException, FileValidationException{
        logger.info("Upload request for '{}' in logical path '{}'", file.getOriginalFilename(), logicalParentPath);

        // Do not allow user to create files with empty names or names starting with a dot
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isEmpty() || originalFileName.startsWith(".")) {
            logger.warn("Upload REJECTED: Filename is null, empty, or starts with a dot: {}", originalFileName);
            throw new FileValidationException("Invalid filename. Files cannot be hidden or have no name.");
        }

        // Make sure that parent path exists in file node database
        createVirtualPath(logicalParentPath);

        AppConfig config = configService.getConfig();
        long fileSize = file.getSize();

        // Size validation
        if (fileSize > (long) config.getServer().getMaxUploadSizeMB() * 1024 * 1024) {
            throw new FileValidationException("File size exceeds the maximum upload limit.");
        }

        // Find a unique filename in the target folder
        String finalFileName = getUniqueFileName(logicalParentPath, originalFileName);
        String finalLogicalPath = Paths.get(logicalParentPath, finalFileName).toString().replace("\\", "/");

        // Build report message
        String message;
        if (!finalFileName.equals(originalFileName)) {
            logger.warn("CONFLICT: Original name was taken. Saving as: {}", finalFileName);
            message = "File uploaded successfully and renamed to '" + finalFileName + "' to avoid conflict.";
        } else {
            message = "File uploaded successfully.";
        }

        // Find best storage path and save the file
        String bestStoragePath = findBestStoragePath(fileSize);
        String userName = logicalParentPath.split("/")[0];

        // Use a unique physical name (utilize UUID)
        String uniquePhysicalName = UUID.randomUUID().toString() + "-" + originalFileName;
        Path physicalPath = Paths.get(bestStoragePath, userName, uniquePhysicalName);

        Files.createDirectories(physicalPath.getParent());
        file.transferTo(physicalPath);
        logger.info("File saved successfully to new physical path: {}", physicalPath);

        // Always create a new node
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
        nodeToSave.setMimeType(file.getContentType());

        // Save, translate and return complete report
        FileNode savedNode = fileIndexService.addOrUpdateNode(nodeToSave);

        logService.logTransfer(userName, "UPLOAD", finalLogicalPath, "Size: " + fileSize + " bytes");

        return new FileOperationResponse(message, toFileInfo(savedNode));
    }

    public Resource getResource(String logicalPath) throws IOException {
        logger.info("Resource request for logical path: {}", logicalPath);

        // Ask file node DB about the node
        FileNode node = fileIndexService.getNode(logicalPath);
        if (node == null) {
            throw new IOException("File not found in index: " + logicalPath);
        }

        String userName = logicalPath.split("/")[0];
        logService.logTransfer(userName, "DOWNLOAD", logicalPath);

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
    public FileOperationResponse deleteResource(String logicalPath) throws IOException, FileValidationException {
        AppConfig config = configService.getConfig();
        logger.warn("Delete request for logical path: {}", logicalPath);

        FileNode rootNodeToDelete = fileIndexService.getNode(logicalPath);
        if (rootNodeToDelete == null) {
            throw new IOException("Resource to delete not found in index: " + logicalPath);
        }

        String userName = rootNodeToDelete.getParentPath().split("/")[0];

        if (config.getTrashCan().isEnabled()) {
            String trashParentPath = userName + "/trash";
            String newFileNameInTrash = getUniqueFileName(trashParentPath, rootNodeToDelete.getFileName());
            String newLogicalPathInTrash = Paths.get(trashParentPath, newFileNameInTrash).toString().replace("\\", "/");

            logger.info("Moving resource to trash as: {}", newLogicalPathInTrash);

            // Call move engine and catch its report
            FileOperationResponse moveResponse = moveResource(logicalPath, newLogicalPathInTrash);

            logService.logTransfer(userName, "TRASH", logicalPath, "Moved to: " + newLogicalPathInTrash);

            // Build a new, precise report
            return new FileOperationResponse("Resource moved to trash successfully.", moveResponse.node());

        } else {
            logger.warn("Trash can is disabled. Permanently deleting resources.");
            deleteRecursively(logicalPath);

            logService.logTransfer(userName, "DELETE_PERMANENT", logicalPath);

            // Return a report about permanent deletion
            return new FileOperationResponse("Resource permanently deleted.", toFileInfo(rootNodeToDelete));
        }
    }

    @Transactional
    public FileOperationResponse restoreResource(String logicalPathInTrash) throws IOException, FileValidationException {
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
        String targetLogicalPath = Paths.get(restoreParentPath, rootNodeToRestore.getFileName()).toString().replace("\\", "/");
        String userName = logicalPathInTrash.split("/")[0];

        logger.info("Restoring resource from {} to {}", logicalPathInTrash, targetLogicalPath);

        // Call the move engine and catch its report
        FileOperationResponse moveResponse = moveResource(logicalPathInTrash, targetLogicalPath);

        logService.logTransfer(userName, "RESTORE", logicalPathInTrash, "Restored to: " + moveResponse.node().logicalPath());

        // Build a new, precise report
        String message = "Resource restored successfully.";
        // Check, whether file name had to be changed
        if (!moveResponse.node().logicalPath().equals(targetLogicalPath)) {
            message = "Resource restored successfully and renamed to '" + moveResponse.node().name() + "' to avoid conflict.";
        }

        return new FileOperationResponse(message, moveResponse.node());
    }

    @Transactional
    public FileOperationResponse moveResource(String oldLogicalPath, String newLogicalPath) throws IOException, FileValidationException {
        logger.info("Universal Smart Move: from [{}] to [{}]", oldLogicalPath, newLogicalPath);

        // Do not allow user to move resources to destinations starting with a dot
        String finalName = Paths.get(newLogicalPath).getFileName().toString();
        if (finalName.startsWith(".")) {
            logger.warn("Move REJECTED: Destination name starts with a dot. Path: {}", newLogicalPath);
            throw new FileValidationException("Invalid destination name. Files and folders cannot start with a dot.");
        }

        // Validate source
        FileNode rootNodeToMove = fileNodeRepository.findByLogicalPath(oldLogicalPath).orElse(null);
        if (rootNodeToMove == null) {
            throw new IOException("Source resource not found in index: " + oldLogicalPath);
        }

        String originalParentPath = rootNodeToMove.getParentPath();
        String userName = originalParentPath.split("/")[0];

        // Handle destination conflicts
        String finalLogicalPath = newLogicalPath;
        String finalFileName = Paths.get(newLogicalPath).getFileName().toString();
        String targetParentPath = Paths.get(newLogicalPath).getParent().toString().replace("\\", "/");

        // Build a report message
        String message;
        if (fileNodeRepository.existsByLogicalPath(newLogicalPath)) {
            logger.warn("CONFLICT: Destination {} exists. Finding unique name...", newLogicalPath);
            finalFileName = getUniqueFileName(targetParentPath, finalFileName);
            finalLogicalPath = Paths.get(targetParentPath, finalFileName).toString().replace("\\", "/");
            logger.warn("CONFLICT RESOLVED: Renaming moved resource to: {}", finalLogicalPath);
            message = "Resource moved successfully and renamed to '" + finalFileName + "' to avoid conflict.";
        } else {
            message = "Resource moved successfully.";
        }

        // Get all nodes to move
        List<FileNode> nodesToMove = fileNodeRepository.findByLogicalPathStartingWith(oldLogicalPath);
        logger.info("Moving {} nodes...", nodesToMove.size());

        // Collect updated nodes in a list
        List<FileNode> updatedNodes = new ArrayList<>();

        for (FileNode node : nodesToMove) {
            String oldSubPath = node.getLogicalPath().substring(oldLogicalPath.length());
            String calculatedNewLogicalPath = finalLogicalPath + oldSubPath;
            String calculatedNewParentPath = Paths.get(calculatedNewLogicalPath).getParent().toString().replace("\\", "/");

            node.setLogicalPath(calculatedNewLogicalPath);
            node.setParentPath(calculatedNewParentPath);
            node.setModifiedAt(Instant.now());

            if (node.getLogicalPath().equals(calculatedNewLogicalPath)) {
                node.setFileName(finalFileName);
                node.setRestorePath(originalParentPath);
            }

            updatedNodes.add(node); // Append to list instead of calling fileIndexService
        }

        // Save everything at once and perform one backup
        fileIndexService.addOrUpdateNodes(updatedNodes);

        logService.logTransfer(userName, "MOVE", oldLogicalPath, "To: " + finalLogicalPath);
        logger.info("Move complete. Root node '{}' is now at '{}'", oldLogicalPath, finalLogicalPath);
        final String pathForFilter = finalLogicalPath;

        // Find updated root on the list
        FileNode savedRootNode = updatedNodes.stream()
                .filter(n -> n.getLogicalPath().equals(pathForFilter))
                .findFirst()
                .orElse(rootNodeToMove);

        // Return a complete report
        return new FileOperationResponse(message, toFileInfo(savedRootNode));
    }

    public List<FileInfo> listFiles(String logicalPath) {
        logger.info("List files request for logical path: {}", logicalPath);
        List<FileNode> nodes = fileIndexService.listFiles(logicalPath);
        
        // Map FileNode to FileInfo
        return nodes.stream()
                .map(this::toFileInfo)
                .collect(Collectors.toList());
    }

    public List<FileInfo> listRecentFiles(String username, int limit, boolean includeMultimediaOnly) {
        if (includeMultimediaOnly) {
            logger.info("Recent multimedia files request for user: {} (limit: {})", username, limit);
        } else  {
            logger.info("Recent files request for user: {} (limit: {})", username, limit);
        }
        
        // Create a Pageable object to ask for the Top N recent files
        Pageable topN = PageRequest.of(0, limit); // 0 means first page, limit means size
        
        // Define the prefix for the user
        String prefix = username + "/";

        // Prepare variable for results
        List<FileNode> nodes;
        
        // Call repository method
        if (includeMultimediaOnly) {
            nodes = fileNodeRepository.findRecentMultimediaFiles(prefix, topN);
        } else  {
            nodes = fileNodeRepository.findRecentFiles(prefix, topN);
        }

        // Translate the results to safe DTOs
        return nodes.stream()
                .map(this::toFileInfo)
                .collect(Collectors.toList());
    }

    @Transactional
    public FileOperationResponse createVirtualPath(String logicalPath) throws IOException, FileValidationException {
        // Do not allow user to create paths with empty names, containing multiple dots or starting with a dot
        if (logicalPath == null || logicalPath.contains("..") || logicalPath.isBlank()) {
            throw new FileValidationException("Invalid path. Path cannot be null, empty, or contain '..'.");
        }
        String[] segments = logicalPath.split("/");
        for (String segment : segments) {
            if (segment.startsWith(".")) {
                logger.warn("Create virtual path REJECTED: Path segment starts with a dot. Path: {}", logicalPath);
                throw new FileValidationException("Invalid path name. Folders or subfolders cannot start with a dot.");
            }
        }

        // Check if this path already exists
        FileNode existingNode = fileNodeRepository.findByLogicalPath(logicalPath).orElse(null);
        if (existingNode != null) {
            // Return a report
            return new FileOperationResponse("Folder already exists.", toFileInfo(existingNode));
        }

        // Calculate parent info
        Path path = Paths.get(logicalPath);
        Path parent = path.getParent();
        String parentPathStr;

        // Handle the recursive call (if needed)
        if (parent == null) {
            // This is a root folder (e.g. "admin"). Its parent is the virtual "/"
            parentPathStr = "/";
        } else {
            // This is a subfolder (e.g. "admin/testy")
            // Make sure its parent ("admin") exists first
            parentPathStr = parent.toString().replace("\\", "/");
            createVirtualPath(parentPathStr); // Recursive call (ignore its report)
        }

        // It is verified that the parent exists, create the current folder
        logger.info("Auto-creating virtual folder in DB: {}", logicalPath);

        String userName = logicalPath.split("/")[0];
        logService.logTransfer(userName, "CREATE_FOLDER", logicalPath);

        FileNode folderNode = new FileNode();
        folderNode.setLogicalPath(logicalPath);
        folderNode.setParentPath(parentPathStr);
        folderNode.setFileName(path.getFileName().toString());
        folderNode.setDirectory(true);
        folderNode.setPhysicalPath("virtual");
        folderNode.setSize(0);
        folderNode.setCreatedAt(Instant.now());
        folderNode.setModifiedAt(Instant.now());
        folderNode.setRestorePath(null);

        // Save, translate and return a complete report
        FileNode savedNode = fileIndexService.addOrUpdateNode(folderNode);
        return new FileOperationResponse("Folder created successfully.", toFileInfo(savedNode));
    }

    // Mapper FileNode -> FileInfo (DTO)
    private FileInfo toFileInfo(FileNode node) {
        return new FileInfo(
                node.getLogicalPath(),
                node.getParentPath(),
                node.getFileName(),
                node.isDirectory(),
                node.getSize(),
                node.getCreatedAt() != null ? node.getCreatedAt().toString() : "N/A",
                node.getModifiedAt() != null ? node.getModifiedAt().toString() : "N/A");
    }

    // Helper methods

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
    protected void deleteRecursively(String logicalPath) throws IOException, FileValidationException {
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

    private String findBestStoragePath(long requiredSpace) throws IOException, FileValidationException {
        AppConfig config = configService.getConfig();
        List<String> paths = config.getStorage().getPaths();
        if (paths == null || paths.isEmpty()) throw new IOException("No storage paths configured!");

        long quotaGB = config.getStorage().getQuotaGB();

        if (quotaGB > 0) {
            long quotaBytes = quotaGB * 1024L * 1024L * 1024L;

            // Use method from StorageMetricsService to calculate current used space
            long currentTotalUsedBytes = storageMetricsService.calculateTotalSize(paths);

            if (currentTotalUsedBytes + requiredSpace > quotaBytes) {
                throw new FileValidationException("Storage Quota Exceeded! The system limit is " + quotaGB + " GB.");
            }
        }

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

        if (bestPath == null || maxFreeSpace < requiredSpace) throw new FileValidationException("Not enough space on any storage device.");

        return bestPath;
    }
}