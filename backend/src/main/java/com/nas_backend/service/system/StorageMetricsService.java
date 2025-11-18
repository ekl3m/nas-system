package com.nas_backend.service.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class StorageMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(StorageMetricsService.class);
    private final ShellService shellService;

    public StorageMetricsService(ShellService shellService) {
        this.shellService = shellService;
    }

    // Calculate total size across multiple storage paths
    public long calculateTotalSize(List<String> paths) throws IOException {
        long totalBytes = 0;
        for (String pathStr : paths) {
            totalBytes += calculateDirectorySize(Paths.get(pathStr));
        }
        return totalBytes;
    }

    // Engine to calculate directory size
    public long calculateDirectorySize(Path path) throws IOException {
        if (!Files.exists(path))
            return 0;

        // Native 'du' command for Linux systems (fast option)
        if (System.getProperty("os.name").toLowerCase().contains("linux")) {
            try {
                // -s: summarize, -b: bytes
                String command = "du -sb " + path.toAbsolutePath();
                String result = shellService.executeCommand(command);

                if (result != null && !result.isBlank()) {
                    String sizeStr = result.trim().split("\\s+")[0];
                    return Long.parseLong(sizeStr);
                }
            } catch (Exception e) {
                logger.warn("Native 'du' failed, falling back to Java NIO. Error: {}", e.getMessage());
            }
        }

        // Fallback: Java NIO (safe option)
        try {
            final AtomicLong size = new AtomicLong(0);
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    size.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
            return size.get();
        } catch (IOException e) {
            logger.error("Failed to calculate directory size via Java NIO for: {}", path, e);
            return 0;
        }
    }
}