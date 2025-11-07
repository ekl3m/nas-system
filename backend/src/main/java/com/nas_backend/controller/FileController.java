package com.nas_backend.controller;

import java.io.IOException;
import java.nio.file.Paths;
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
import com.nas_backend.model.UserConfig;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final AuthService authService; // Injecting authService to validate tokens

    // DTO Records

    public record CreateFolderRequest(String logicalPath) {}
    public record MoveRequest(String fromPath, String toPath) {}

    public FileController(FileService fileService, AuthService authService) {
        this.fileService = fileService;
        this.authService = authService;
    }

    private String requireValidUser(String authHeader) {
        String token = authService.extractToken(authHeader);
        UserConfig user = authService.getUserFromToken(token);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or missing token");
        }
        
        return user.getUsername();
    }

    @GetMapping("/list")
    public ResponseEntity<?> listFiles(@RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestParam(name = "path", required = false, defaultValue = "") String path) {
        String username = requireValidUser(authHeader);
        String finalUserPath;

        if (path == null || path.isEmpty() || path.equals("/")) {
            // If user asks for "root", return him his own folder e.g. "admin"
            finalUserPath = username;
        } else {
            // If user asks for a subfolder, build a complete path
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            finalUserPath = Paths.get(username, path).toString().replace("\\", "/");
        }

        List<FileInfo> files = fileService.listFiles(finalUserPath);

        return ResponseEntity.ok(files);
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
            @RequestParam(name = "file") MultipartFile file,
            @RequestParam(name = "overwrite", required = false, defaultValue = "false") boolean overwrite) {
        String username = requireValidUser(authHeader);

        try {
            String userPath = username + "/" + path;
            fileService.uploadFile(userPath, file, overwrite);
            return ResponseEntity.ok(Map.of("message", "File uploaded successfully"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied: path outside storage"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteFile(@RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestParam(name = "path") String path) {
        String username = requireValidUser(authHeader);

        try {
            String userPath = username + "/" + path;
            fileService.deleteResource(userPath);
            return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied: path outside storage"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Delete failed: " + e.getMessage()));
        }
    }

    @PutMapping("/move")
    public ResponseEntity<?> moveResource(@RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestBody MoveRequest moveRequest) {
        String username = requireValidUser(authHeader);

        String userFromPath = Paths.get(username, moveRequest.fromPath()).toString().replace("\\", "/");
        String userToPath = Paths.get(username, moveRequest.toPath()).toString().replace("\\", "/");

        // Do not move things in trash
        String trashPrefix = username + "/trash";
        if (userFromPath.startsWith(trashPrefix) || userToPath.startsWith(trashPrefix)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Move/Rename operations are not allowed on items in the trash. Please restore the item first."));
        }

        // Path validation
        if (moveRequest.fromPath() == null || moveRequest.toPath() == null ||
                moveRequest.fromPath().contains("..") || moveRequest.toPath().contains("..")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid paths specified."));
        }

        try {
            // Call moving engine
            fileService.moveResource(userFromPath, userToPath);
            return ResponseEntity.ok(Map.of("message", "Resource moved successfully to " + moveRequest.toPath()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Move failed: " + e.getMessage()));
        }
    }

    @PostMapping("/folders/create")
    public ResponseEntity<?> createFolder(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestBody CreateFolderRequest request) {
        
        String username = requireValidUser(authHeader);
        String fullLogicalPath = Paths.get(username, request.logicalPath()).toString().replace("\\", "/");

        try {
            fileService.createVirtualPath(fullLogicalPath);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Folder created successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}