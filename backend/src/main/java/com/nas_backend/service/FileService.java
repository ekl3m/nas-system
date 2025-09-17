package com.nas_backend.service;

import com.nas_backend.model.FileInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileService {

    @Value("${nas.storage.path}")
    private String storagePath;

    public List<FileInfo> listFiles(String path) throws IOException {
        List<FileInfo> files = new ArrayList<>();
        File root = new File(storagePath).getCanonicalFile();
        File folder = new File(storagePath, path != null ? path : "").getCanonicalFile();

        // Do not allow users to leave root NAS directory
        if (!folder.getPath().startsWith(root.getPath())) {
            throw new SecurityException("Access denied: path outside storage");
        }

        if (folder.exists() && folder.isDirectory()) {
            File parent = folder.getParentFile();
            String parentPath = (parent != null && parent.getCanonicalPath().startsWith(root.getPath())) ? root.toURI().relativize(parent.toURI()).getPath() : null;

            for (File file : folder.listFiles()) {
                if (file.isHidden()) continue;
                String lastModified = Instant.ofEpochMilli(file.lastModified()).toString();
                String relativeParentPath = parentPath != null ? parentPath : "";
                files.add(new FileInfo(file.getName(), file.isDirectory(), file.length(), lastModified, relativeParentPath));
            }
        }

        return files;
    }
}