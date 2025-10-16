package com.nas_backend.service;

import com.nas_backend.model.AppConfig;
import com.nas_backend.model.FileInfo;
import com.nas_backend.model.FileMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
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
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    private final AppConfigService configService;
    private final FileIndexService fileIndexService;

    public FileService(AppConfigService appConfigService, FileIndexService fileIndexService) {
        this.configService = appConfigService;
        this.fileIndexService = fileIndexService;
    }

    public void uploadFile(String logicalPath, MultipartFile file, boolean overwrite) throws IOException {
        logger.info("Upload request for '{}' in logical path '{}'", file.getOriginalFilename(), logicalPath);
        AppConfig config = configService.getConfig();
        long fileSize = file.getSize();

        // Check file size limit
        if (fileSize > (long) config.getServer().getMaxUploadSizeMB() * 1024 * 1024) {
            throw new IOException("File size exceeds the maximum upload limit.");
        }

        // Step 1: Find best storage path to save the file
        String bestStoragePath = findBestStoragePath(fileSize);

        // Step 2: Build full logical and physical file path
        Path physicalDirectory = Paths.get(bestStoragePath, logicalPath);
        Files.createDirectories(physicalDirectory); // Make sure that target folder exists

        File destinationFile = physicalDirectory.resolve(file.getOriginalFilename()).toFile();
        String finalLogicalPath = Paths.get(logicalPath, file.getOriginalFilename()).toString().replace("\\", "/");

        // Step 3: Check in index whether the file already exists
        if (fileIndexService.getMetadata(finalLogicalPath) != null && !overwrite) {
            throw new IOException("File already exists in the index. Set overwrite=true to replace it.");
        }

        // Step 4: Save the file
        file.transferTo(destinationFile);
        logger.info("File saved successfully to physical path: {}", destinationFile.getAbsolutePath());

        // Step 5: Create metadata and append to index
        Instant now = Instant.now();
        FileMetadata metadata = new FileMetadata(
                finalLogicalPath,
                destinationFile.getAbsolutePath(),
                fileSize,
                now, // createdAt
                now, // modifiedAt
                false // isDirectory
        );
        fileIndexService.addOrUpdateFile(metadata);
        logger.info("File index updated for logical path: {}", finalLogicalPath);
    }

    public Resource getResource(String logicalPath) throws IOException {
        logger.info("Resource request for logical path: {}", logicalPath);

        // Step 1: Ask index about the metadata
        FileMetadata metadata = fileIndexService.getMetadata(logicalPath);
        if (metadata == null) {
            throw new IOException("File not found in index: " + logicalPath);
        }

        // Step 2: Use physical path from metadata
        File file = new File(metadata.getPhysicalPath());
        if (!file.exists()) {
            logger.error("File inconsistency! Found in index but not on disk: {}", metadata.getPhysicalPath());
            fileIndexService.removeFile(logicalPath); // Opcjonalnie: auto-naprawa indeksu
            throw new IOException("File not found on disk, index corrected.");
        }

        if (metadata.isDirectory()) {
            return getFolderAsZip(file);
        } else {
            return new FileSystemResource(file);
        }
    }

    public void deleteResource(String logicalPath) throws IOException {
        logger.warn("Delete request for logical path: {}", logicalPath);

        // Step 1: Find the file in index
        FileMetadata metadata = fileIndexService.getMetadata(logicalPath);
        if (metadata == null) {
            throw new IOException("File to delete not found in index: " + logicalPath);
        }

        // Step 2: Delete physical file
        File file = new File(metadata.getPhysicalPath());
        if (file.exists()) {
            if (metadata.isDirectory()) {
                deleteDirectoryRecursively(file);
            } else {
                Files.delete(file.toPath());
            }
            logger.info("Physical file deleted from: {}", metadata.getPhysicalPath());
        } else {
            logger.warn("Physical file was already deleted, but existed in index: {}", metadata.getPhysicalPath());
        }

        // Step 3: Delete file from index
        fileIndexService.removeFile(logicalPath);
        logger.info("File removed from index: {}", logicalPath);
    }

    public List<FileInfo> listFiles(String logicalPath) {
        logger.info("List files request for logical path: {}", logicalPath);

        // Step 1: Step 1: Obtain metadata list from index
        List<FileMetadata> metadataList = fileIndexService.listFiles(logicalPath);

        // Step 2: Convert (map) metadata to DTO objects (FileInfo) for frontend
        return metadataList.stream()
                .map(this::toFileInfo)
                .collect(Collectors.toList());
    }

    // Private helper methods

    private String findBestStoragePath(long requiredSpace) throws IOException {
        List<String> paths = configService.getConfig().getStorage().getPaths();
        if (paths.isEmpty())
            throw new IOException("No storage paths configured!");

        String bestPath = null;
        long maxFreeSpace = -1;

        for (String pathStr : paths) {
            Path path = Paths.get(pathStr);
            if (Files.notExists(path))
                Files.createDirectories(path);

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

    // Metadata to FileInfo (DTO) mapper
    private FileInfo toFileInfo(FileMetadata metadata) {
        Path logicalPath = Paths.get(metadata.getLogicalPath());
        String name = logicalPath.getFileName().toString();
        Path parent = logicalPath.getParent();
        String parentPath = (parent != null) ? parent.toString().replace("\\", "/") : "";

        return new FileInfo(
                name,
                metadata.isDirectory(),
                metadata.getSize(),
                metadata.getModifiedAt().toString(),
                parentPath);
    }

    private Resource getFolderAsZip(File folder) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zipFileRecursively(folder, folder.getName(), zos);
        }
        return new ByteArrayResource(baos.toByteArray());
    }

    private void zipFileRecursively(File fileToZip, String fileName, ZipOutputStream zos) throws IOException {
        if (fileToZip.isHidden())
            return;

        if (fileToZip.isDirectory()) {
            if (!fileName.endsWith("/"))
                fileName += "/";

            zos.putNextEntry(new ZipEntry(fileName));
            zos.closeEntry();

            for (File childFile : fileToZip.listFiles()) {
                zipFileRecursively(childFile, fileName + childFile.getName(), zos);
            }

            return;
        }

        try (FileInputStream fis = new FileInputStream(fileToZip)) {
            ZipEntry zipEntry = new ZipEntry(fileName);
            zos.putNextEntry(zipEntry);
            byte[] bytes = new byte[1024];
            int length;

            while ((length = fis.read(bytes)) >= 0) {
                zos.write(bytes, 0, length);
            }
        }
    }

    private void deleteDirectoryRecursively(File dir) throws IOException {
        File[] entries = dir.listFiles();

        if (entries != null) {
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    deleteDirectoryRecursively(entry);
                } else {
                    Files.delete(entry.toPath());
                }
            }
        }

        Files.delete(dir.toPath());
    }
}