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
    public void uploadFile(String logicalParentPath, MultipartFile file, boolean overwrite) throws IOException {
        logger.info("Upload request for '{}' in logical path '{}'", file.getOriginalFilename(), logicalParentPath);
        AppConfig config = configService.getConfig();
        long fileSize = file.getSize();
        String originalFileName = file.getOriginalFilename();

        // Size validation
        if (fileSize > (long) config.getServer().getMaxUploadSizeMB() * 1024 * 1024) {
            throw new IOException("File size exceeds the maximum upload limit.");
        }

        // Build complete logical path
        String finalLogicalPath = Paths.get(logicalParentPath, originalFileName).toString().replace("\\", "/");

        // Verify whether a file node using this logical path already exists
        FileNode existingNode = fileIndexService.getNode(finalLogicalPath);

        // Validate
        if (existingNode != null && !overwrite) {
            // File using this logical path exists and user did not agree to overwrite it
            throw new IOException(
                    "A file with this name already exists at this location. Set overwrite=true to replace it.");
        }

        // Find best storage path and save the file
        String bestStoragePath = findBestStoragePath(fileSize);
        String userName = logicalParentPath.split("/")[0];
        String uniquePhysicalName = UUID.randomUUID().toString() + "-" + originalFileName;
        Path physicalPath = Paths.get(bestStoragePath, userName, uniquePhysicalName);

        Files.createDirectories(physicalPath.getParent());
        file.transferTo(physicalPath);
        logger.info("File saved successfully to new physical path: {}", physicalPath);

        // Prepare file node DB entry (update old or create new)
        FileNode nodeToSave;

        if (existingNode != null) {
            // OVERWRITE MODE
            nodeToSave = existingNode;
            logger.warn("Overwrite mode: Found existing node for: {}", finalLogicalPath);

            // Delete old physical file as an attempt to spare it from becoming an orphan
            logger.info("Attempting to delete old physical file at: {}", existingNode.getPhysicalPath());
            File oldPhysicalFile = new File(existingNode.getPhysicalPath());
            if (oldPhysicalFile.exists()) {
                try {
                    Files.delete(oldPhysicalFile.toPath());
                    logger.info("Deleted old physical file successfully.");
                } catch (IOException e) {
                    logger.warn("Could not delete old physical file: {}", oldPhysicalFile.getAbsolutePath(), e);
                }
            }
        } else {
            // NEW FILE MODE, create an entry
            nodeToSave = new FileNode();
            nodeToSave.setCreatedAt(Instant.now());
        }

        // Set or update all fields
        nodeToSave.setLogicalPath(finalLogicalPath);
        nodeToSave.setParentPath(logicalParentPath);
        nodeToSave.setPhysicalPath(physicalPath.toString()); // New file physical path
        nodeToSave.setFileName(originalFileName);
        nodeToSave.setDirectory(false);
        nodeToSave.setSize(fileSize);
        nodeToSave.setModifiedAt(Instant.now());
        nodeToSave.setRestorePath(null); // Must remain null until deletion

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

        // Find root node to delete
        FileNode rootNodeToDelete = fileIndexService.getNode(logicalPath);
        if (rootNodeToDelete == null) {
            throw new IOException("Resource to delete not found in index: " + logicalPath);
        }

        // Store original path, so that it remains restorable
        String originalParentPath = rootNodeToDelete.getParentPath();

        if (config.getTrashCan().isEnabled()) {
            // Virtual trashcan logic
            logger.info("Moving resource and its children to virtual trash...");
            String userName = originalParentPath.split("/")[0]; // "admin"
            String trashParentPath = userName + "/trash";

            // Create a new base file path in trash e.g. "admin/trash/testy-uuid"
            String newBaseNameInTrash = rootNodeToDelete.getFileName() + "-"
                    + UUID.randomUUID().toString().substring(0, 8);
            String newBasePathInTrash = Paths.get(trashParentPath, newBaseNameInTrash).toString().replace("\\", "/");

            // Find all nodes to move (this single one or a directory and its children)
            List<FileNode> nodesToMove = fileNodeRepository.findByLogicalPathStartingWith(logicalPath);

            for (FileNode node : nodesToMove) {
                // Compute a new logical path, sparing the structure e.g. "admin/testy/plik.txt" -> "admin/trash/testy-uuid/plik.txt"
                String oldSubPath = node.getLogicalPath().substring(logicalPath.length()); // np. "" lub "/plik.txt"
                String newLogicalPath = newBasePathInTrash + oldSubPath;
                String newParentPath = new File(newLogicalPath).getParent().replace("\\", "/");

                // Save the origin path
                if (node.getLogicalPath().equals(logicalPath)) {
                    node.setRestorePath(originalParentPath);
                }

                // "Move" it virtually
                node.setLogicalPath(newLogicalPath);
                node.setParentPath(newParentPath);
                node.setModifiedAt(Instant.now());

                fileIndexService.addOrUpdateNode(node);
            }
            logger.info("All {} nodes related to {} virtually moved to new trash path: {}", nodesToMove.size(),
                    logicalPath, newBasePathInTrash);

        } else {
            // Permanent deletion logic
            logger.warn("Trash can is disabled. Permanently deleting resources.");
            deleteRecursively(logicalPath);
        }
    }

    @Transactional
    public void moveResource(String oldLogicalPath, String newLogicalPath) throws IOException {
        logger.info("Move/Rename request from '{}' to '{}'", oldLogicalPath, newLogicalPath);

        // Validate
        FileNode node = fileIndexService.getNode(oldLogicalPath);
        if (node == null) {
            throw new IOException("Source resource not found in index: " + oldLogicalPath);
        }
        if (fileIndexService.nodeExists(newLogicalPath)) {
            throw new IOException("Destination logical path already exists: \"" + newLogicalPath + "\"");
        }
        
        // Just replace an entry in file node DB
        String newParentPath = new File(newLogicalPath).getParent().replace("\\", "/");
        String newFileName = new File(newLogicalPath).getName();
        
        node.setLogicalPath(newLogicalPath);
        node.setParentPath(newParentPath);
        node.setFileName(newFileName);
        node.setModifiedAt(Instant.now());

        fileIndexService.addOrUpdateNode(node);

        // If it was a directory, update its children paths
        if (node.isDirectory()) {
            List<FileNode> children = fileNodeRepository.findByLogicalPathStartingWith(oldLogicalPath + "/");
            for (FileNode child : children) {
                String oldChildPath = child.getLogicalPath();
                String newChildPath = oldChildPath.replaceFirst(oldLogicalPath, newLogicalPath);
                String newChildParentPath = new File(newChildPath).getParent().replace("\\", "/");
                
                child.setLogicalPath(newChildPath);
                child.setParentPath(newChildParentPath);
                fileIndexService.addOrUpdateNode(child);
            }
            logger.info("Updated {} children nodes for renamed folder.", children.size());
        }
        
        logger.info("Resource virtually moved in index. Physical file was NOT touched.");
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