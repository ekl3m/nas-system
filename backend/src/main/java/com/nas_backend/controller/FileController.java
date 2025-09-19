package com.nas_backend.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nas_backend.service.FileService;
import com.nas_backend.model.FileInfo;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping
    public ResponseEntity<?> listFiles(@RequestParam(name = "path", required = false, defaultValue = "") String path) {
        try {
            List<FileInfo> files = fileService.listFiles(path);
            return ResponseEntity.ok(files);
        } catch (SecurityException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Access denied: path outside storage");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/download")
    public ResponseEntity<?> download(@RequestParam(name = "path", required = true) String path) {
        try {
            Resource resource = fileService.getResource(path);
            String filename = resource instanceof FileSystemResource 
                                ? ((FileSystemResource) resource).getFile().getName() 
                                : path.substring(path.lastIndexOf('/') + 1) + ".zip";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (SecurityException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Access denied: path outside storage");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "File or folder not found / internal error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
