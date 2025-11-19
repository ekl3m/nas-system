package com.nas_backend.controller;

import com.nas_backend.model.dto.SystemStatsResponse;
import com.nas_backend.model.security.UserConfig;
import com.nas_backend.service.AuthService;
import com.nas_backend.service.system.BackupService;
import com.nas_backend.service.system.LogService;
import com.nas_backend.service.system.SystemAdminService;
import com.nas_backend.service.system.SystemStatsService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.tags.*;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System", description = "Endpoints for system management and monitoring")
public class SystemController {

    private final SystemStatsService systemStatsService;
    private final AuthService authService;
    private final SystemAdminService systemAdminService;
    private final LogService logService;
    private final BackupService backupService;

    public SystemController(SystemStatsService systemStatsService, AuthService authService, SystemAdminService systemAdminService, LogService logService,
                            BackupService backupService) {
        this.systemStatsService = systemStatsService;
        this.authService = authService;
        this.systemAdminService = systemAdminService;
        this.logService = logService;
        this.backupService = backupService;
    }

    private String requireValidUser(String authHeader) {
        String token = authService.extractToken(authHeader);
        UserConfig user = authService.getUserFromToken(token);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or missing token");
        }
        return user.getUsername();
    }

    @GetMapping("/stats")
    @Operation(summary = "Get system statistics", description = "Retrieve current system statistics including CPU, memory and disk usage")
    public ResponseEntity<SystemStatsResponse> getSystemStats(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        requireValidUser(authHeader);

        // Call the service to gather stats
        SystemStatsResponse stats = systemStatsService.getSystemStats();

        // Return the report using SystemStatsResponse DTO
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/reboot")
    @Operation(summary = "Reboot system", description = "Reboot the entire system")
    public ResponseEntity<?> rebootSystem(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        requireValidUser(authHeader); 
        
        systemAdminService.rebootSystem();
        
        // Return "OK" immediately, before the machine shuts down
        return ResponseEntity.ok(Map.of("message", "System is rebooting now."));
    }

    @PostMapping("/shutdown")
    @Operation(summary = "Shutdown system", description = "Shutdown the entire system")
    public ResponseEntity<?> shutdownSystem(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        requireValidUser(authHeader); 
        
        systemAdminService.shutdownSystem();
        
        // Return "OK" immediately, before the machine shuts down
        return ResponseEntity.ok(Map.of("message", "System is shutting down now."));
    }

    @GetMapping("/logs/events")
    @Operation(summary = "Get event logs", description = "Retrieve logs related to system events")
    public ResponseEntity<List<String>> getEventLogs(@RequestHeader(name = "Authorization", required = false) String authHeader) {

        requireValidUser(authHeader);

        // Read nas-events.log
        List<String> logs = logService.getLogFileContent("nas-events.log");
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/logs/transfers")
    @Operation(summary = "Get transfer logs", description = "Retrieve logs related to file transfers")
    public ResponseEntity<List<String>> getTransferLogs(@RequestHeader(name = "Authorization", required = false) String authHeader) {

        requireValidUser(authHeader);

        // Read nas-transfers.log
        List<String> logs = logService.getLogFileContent("nas-transfers.log");
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/logs/system")
    @Operation(summary = "Get system logs", description = "Retrieve logs related to system operations")
    public ResponseEntity<List<String>> getSystemLogs(@RequestHeader(name = "Authorization", required = false) String authHeader) {

        requireValidUser(authHeader);

        // Read nas-system.log
        List<String> logs = logService.getLogFileContent("nas-system.log");
        return ResponseEntity.ok(logs);
    }

    @PostMapping("/backup/start")
    @Operation(summary = "Start file backup", description = "Manually initiate a backup of files")
    public ResponseEntity<?> startFileBackup(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        
        requireValidUser(authHeader);

        backupService.backupFiles();

        return ResponseEntity.ok(Map.of("message", "File backup initiated. Check system logs for details."));
    }
}
