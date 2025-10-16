package com.nas_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nas_backend.model.FileMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FileIndexService {
    private static final Logger logger = LoggerFactory.getLogger(FileIndexService.class);

    private static final String DATA_DIR_NAME = "data";
    private static final String INDEX_FILE_NAME = "index.json";

    private final String indexFilePath;
    private Map<String, FileMetadata> fileIndex = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final AppConfigService configService; // Needed for scanning

    public FileIndexService(AppConfigService appConfigService) {
        this.configService = appConfigService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        String rootPath = System.getProperty("APP_ROOT_PATH");
        this.indexFilePath = Paths.get(rootPath, DATA_DIR_NAME, INDEX_FILE_NAME).toString();
    }

    @PostConstruct
    private void loadIndexOnStartup() {
        File indexFile = new File(this.indexFilePath);
        boolean shouldScan = true;

        if (indexFile.exists() && indexFile.length() > 0) {
            logger.info("Found existing index file at: {}", this.indexFilePath);
            try {
                fileIndex = objectMapper.readValue(indexFile,
                        new TypeReference<ConcurrentHashMap<String, FileMetadata>>() {
                        });
                logger.info("File index loaded successfully. Found {} entries.", fileIndex.size());
                if (!fileIndex.isEmpty()) {
                    shouldScan = false;
                }
            } catch (IOException e) {
                logger.error("Index file is corrupted. Rebuilding by scanning. Error: {}", e.getMessage());
            }
        }

        if (shouldScan) {
            logger.warn("Starting a full scan of storage directories to build/rebuild the index.");
            fileIndex = new ConcurrentHashMap<>();
            scanAndBuildIndex();
        }
    }

    private void scanAndBuildIndex() {
        List<String> storagePaths = configService.getConfig().getStorage().getPaths();
        if (storagePaths == null || storagePaths.isEmpty()) {
            logger.error("Cannot scan storage, no storage paths configured in config.json!");
            return;
        }

        logger.info("Scanning storage paths: {}", storagePaths);
        for (String rootPathStr : storagePaths) {
            Path rootPath = Paths.get(rootPathStr);
            if (Files.notExists(rootPath))
                continue;

            try (Stream<Path> stream = Files.walk(rootPath)) {
                stream.forEach(path -> {
                    File file = path.toFile();
                    if (file.isHidden() || path.equals(rootPath)) {
                        return; // Ignore hidden files/dirs and the root dir itself
                    }

                    try {
                        String physicalPath = file.getCanonicalPath();
                        String logicalPath = physicalPath
                                .substring(new File(rootPathStr).getCanonicalPath().length() + 1)
                                .replace(File.separator, "/");

                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        FileMetadata metadata = new FileMetadata(
                                logicalPath, physicalPath, attrs.size(),
                                attrs.creationTime().toInstant(), attrs.lastModifiedTime().toInstant(),
                                attrs.isDirectory());
                        fileIndex.put(logicalPath, metadata);

                    } catch (IOException e) {
                        logger.error("Failed to process path during scan: {}", path, e);
                    }
                });
            } catch (IOException e) {
                logger.error("Failed to scan storage path: {}", rootPathStr, e);
            }
        }

        logger.info("Index scan complete. Found {} entries. Saving to file.", fileIndex.size());
        saveIndexToFile();
    }

    @PreDestroy
    private void saveIndexOnShutdown() {
        saveIndexToFile();
    }

    private synchronized void saveIndexToFile() {
        try {
            File indexFile = new File(this.indexFilePath);
            File parentDir = indexFile.getParentFile();
            if (parentDir != null) {
                parentDir.mkdirs();
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(indexFile, fileIndex);
        } catch (IOException e) {
            logger.error("CRITICAL: Could not save index file!", e);
        }
    }

    public void addOrUpdateFile(FileMetadata metadata) {
        fileIndex.put(metadata.getLogicalPath(), metadata);
        saveIndexToFile();
    }

    public void removeFile(String logicalPath) {
        fileIndex.remove(logicalPath);
        saveIndexToFile();
    }

    public FileMetadata getMetadata(String logicalPath) {
        return fileIndex.get(logicalPath);
    }

    public List<FileMetadata> listFiles(String directoryLogicalPath) {
        if (!directoryLogicalPath.endsWith("/") && !directoryLogicalPath.isEmpty()) {
            directoryLogicalPath += "/";
        }
        final String finalPath = directoryLogicalPath;
        return fileIndex.keySet().stream()
                .filter(path -> path.startsWith(finalPath))
                .filter(path -> !path.substring(finalPath.length()).contains("/"))
                .map(fileIndex::get)
                .collect(Collectors.toList());
    }
}