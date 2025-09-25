package com.nas_backend.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.nas_backend.service.AuthService;
import com.nas_backend.service.FileService;
import com.nas_backend.model.FileInfo;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final AuthService authService; // Injecting authService to validate tokens

    public FileController(FileService fileService, AuthService authService) {
        this.fileService = fileService;
        this.authService = authService;
    }

    private String requireValidUser(String authHeader) {
        String token = authService.extractToken(authHeader);
        String username = authService.getUsernameFromToken(token);
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or missing token");
        }
    return username;
}

    @GetMapping("/list")
    public ResponseEntity<?> listFiles(@RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestParam(name = "path", required = false, defaultValue = "") String path) {
        String username = requireValidUser(authHeader);

        try {
            // User folder becomes root path
            String userPath = username + "/" + path;
            List<FileInfo> files = fileService.listFiles(userPath);
            return ResponseEntity.ok(files);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied: path outside storage"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/download")
    public ResponseEntity<?> download(@RequestHeader(name = "Authorization", required = false) String authHeader, @RequestParam(name = "path") String path) {
        String username = requireValidUser(authHeader);

        try {
            String userPath = username + "/" + path;
            Resource resource = fileService.getResource(userPath);
            String filename = resource instanceof FileSystemResource
                    ? ((FileSystemResource) resource).getFile().getName()
                    : path.substring(path.lastIndexOf('/') + 1);

            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied: path outside storage"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "File or folder not found / internal error"));
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestParam(name = "path") String path,
            @RequestParam(name = "file") MultipartFile file) {
        String username = requireValidUser(authHeader);

        try {
            String userPath = username + "/" + path;
            fileService.uploadFile(userPath, file);
            return ResponseEntity.ok(Map.of("message", "File uploaded successfully"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied: path outside storage"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }
}