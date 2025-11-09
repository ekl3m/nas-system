package com.nas_backend.model;

public record FileInfo(
        String logicalPath, // The unqiue logical path (e.g. "admin/docs/file.txt")
        String parentPath, // The logical parent (e.g. "admin/docs")
        String name, // The display name (e.g. "file.txt")
        boolean isDirectory,
        long size,
        String createdAt, // ISO 8601 String - when the file was first uploaded
        String lastModified // ISO 8601 String - when the file was last touched
) {}
