package com.nas_backend.controller;

import com.nas_backend.service.AuthService;
import com.nas_backend.service.file.FileService;
import com.nas_backend.model.dto.FileInfo;
import com.nas_backend.model.dto.FileOperationResponse;
import com.nas_backend.model.dto.request.CreateFolderRequest;
import com.nas_backend.model.dto.request.MoveRequest;
import com.nas_backend.model.dto.request.RestoreRequest;
import com.nas_backend.model.security.UserConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final AuthService authService;
    private final Logger logger = LoggerFactory.getLogger(FileController.class);

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

    // Endpoints

    @GetMapping("/list")
    public ResponseEntity<List<FileInfo>> listFiles(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestParam(name = "path", required = false, defaultValue = "") String path) {

        String username = requireValidUser(authHeader);
        String finalUserPath;

        if (path == null || path.isEmpty() || path.equals("/")) {
            finalUserPath = username;
        } else {
            if (path.startsWith("/"))
                path = path.substring(1);
            finalUserPath = Paths.get(username, path).toString().replace("\\", "/");
        }

        logger.info("Listing files for logical path: {}", finalUserPath);
        List<FileInfo> files = fileService.listFiles(finalUserPath);
        return ResponseEntity.ok(files);
    }

    @PostMapping("/upload")
    public ResponseEntity<FileOperationResponse> upload(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestParam(name = "path") String path,
            @RequestParam(name = "file") MultipartFile file) {

        String username = requireValidUser(authHeader);
        String userPath = Paths.get(username, path).toString().replace("\\", "/");

        try {
            FileOperationResponse response = fileService.uploadFile(userPath, file);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new FileOperationResponse("Upload failed: " + e.getMessage(), null));
        }
    }

    @PostMapping("/folders/create")
    public ResponseEntity<FileOperationResponse> createFolder(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestBody CreateFolderRequest request) {

        String username = requireValidUser(authHeader);
        String fullLogicalPath = Paths.get(username, request.logicalPath()).toString().replace("\\", "/");

        try {
            FileOperationResponse response = fileService.createVirtualPath(fullLogicalPath);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new FileOperationResponse(e.getMessage(), null));
        }
    }

    @PutMapping("/move")
    public ResponseEntity<FileOperationResponse> moveResource(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestBody MoveRequest moveRequest) {

        String username = requireValidUser(authHeader);
        String userFromPath = Paths.get(username, moveRequest.fromPath()).toString().replace("\\", "/");
        String userToPath = Paths.get(username, moveRequest.toPath()).toString().replace("\\", "/");

        // Gatekeeper (prevents messing with trash)
        String trashPrefix = username + "/trash";
        if (userFromPath.startsWith(trashPrefix) || userToPath.startsWith(trashPrefix)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new FileOperationResponse("Move/Rename operations are not allowed on items in the trash. Please restore the item first.", null));
        }

        // Path validation, e.g. checking for '..'
        if (moveRequest.fromPath() == null || moveRequest.toPath() == null ||
                moveRequest.fromPath().isEmpty() || moveRequest.toPath().isEmpty() ||
                moveRequest.fromPath().contains("..") || moveRequest.toPath().contains("..")) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new FileOperationResponse("Invalid paths specified. Paths cannot be empty or contain '..'.", null));
        }

        try {
            FileOperationResponse response = fileService.moveResource(userFromPath, userToPath);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new FileOperationResponse("Move failed: " + e.getMessage(), null));
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<FileOperationResponse> deleteFile(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestParam(name = "path") String path) {

        String username = requireValidUser(authHeader);
        String userPath = Paths.get(username, path).toString().replace("\\", "/");

        try {
            FileOperationResponse response = fileService.deleteResource(userPath);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new FileOperationResponse("Delete failed: " + e.getMessage(), null));
        }
    }

    @PostMapping("/restore")
    public ResponseEntity<FileOperationResponse> restoreResource(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestBody RestoreRequest request) {

        String username = requireValidUser(authHeader);
        String pathInTrash = request.logicalPath();

        if (!pathInTrash.startsWith(username + "/trash/")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new FileOperationResponse("Restore failed: The provided path is not a valid item inside the trash folder.", null));
        }

        try {
            FileOperationResponse response = fileService.restoreResource(pathInTrash);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            if (e.getMessage().startsWith("409 CONFLICT:")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new FileOperationResponse(e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new FileOperationResponse(e.getMessage(), null));
        }
    }

    @GetMapping("/download")
    public ResponseEntity<?> download(@RequestHeader(name = "Authorization", required = false) String authHeader,
            @RequestParam(name = "path") String path) {
        String username = requireValidUser(authHeader);
        String userPath = Paths.get(username, path).toString().replace("\\", "/");

        try {
            Resource resource = fileService.getResource(userPath);
            String filename = Paths.get(userPath).getFileName().toString();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new FileOperationResponse(e.getMessage(), null));
        }
    }
}