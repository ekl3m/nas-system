package com.nas_backend.service;

import com.nas_backend.model.FileInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FileService {

    @Value("${nas.storage.path}")
    private String storagePath;

    public List<FileInfo> listFiles(String path) throws IOException {
        List<FileInfo> files = new ArrayList<>();
        File folder = resolvePath(path);

        if (folder.exists() && folder.isDirectory()) {
            File parent = folder.getParentFile();
            String parentPath = (parent != null && parent.getCanonicalPath().startsWith(new File(storagePath).getCanonicalPath()))
                                ? new File(storagePath).toURI().relativize(parent.toURI()).getPath()
                                : null;

            for (File file : folder.listFiles()) {
                if (file.isHidden()) continue;
                String lastModified = Instant.ofEpochMilli(file.lastModified()).toString();
                String relativeParentPath = parentPath != null ? parentPath : "";
                files.add(new FileInfo(file.getName(), file.isDirectory(), file.length(), lastModified, relativeParentPath));
            }
        }

        return files;
    }

    public Resource getResource(String relativePath) throws IOException {
        File file = resolvePath(relativePath);

        if (!file.exists() || file.isHidden()) {
            throw new IOException("File or folder not found");
        }

        if (file.isDirectory()) {
            return getFolder(relativePath); // Return zipped folder
        } else {
            return getFile(relativePath); // Return an usual file
        }
    }

    public void uploadFile(String relativePath, MultipartFile file) throws IOException {
        File folder = resolvePath(relativePath);

        if (!folder.exists() || !folder.isDirectory()) {
            throw new IOException("Destination folder not found");
        }

        File destination = new File(folder, file.getOriginalFilename()).getCanonicalFile();

        // Save in storage
        file.transferTo(destination);
    }

    public void deleteResource(String relativePath) throws IOException {
        File file = resolvePath(relativePath);

        if (!file.exists()) {
            throw new IOException("File not found");
        }

        if (file.isDirectory()) {
            deleteDirectoryRecursively(file);
        } else {
            Files.delete(file.toPath());
        }
    }

    // Private methods

    private Resource getFile(String relativePath) throws IOException {
        File file = resolvePath(relativePath);

        if (!file.exists() || file.isDirectory() || file.isHidden()) {
            throw new IOException("File not found or inaccessible");
        }

        return new FileSystemResource(file);
    }

    private Resource getFolder(String relativePath) throws IOException {
        File folder = resolvePath(relativePath);

        if (!folder.exists() || !folder.isDirectory()) {
            throw new IOException("Folder not found");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zipFileRecursively(folder, folder.getName(), zos);
        }

        return new ByteArrayResource(baos.toByteArray());
    }

    private File resolvePath(String relativePath) throws IOException {
        File root = new File(storagePath).getCanonicalFile();
        File file = new File(storagePath, relativePath != null ? relativePath : "").getCanonicalFile();

        if (!file.getPath().startsWith(root.getPath())) {
            throw new SecurityException("Access denied: path outside storage");
        }

        return file;
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