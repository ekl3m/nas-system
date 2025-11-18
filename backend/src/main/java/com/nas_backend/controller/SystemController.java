package com.nas_backend.controller;

import com.nas_backend.model.dto.SystemStatsResponse;
import com.nas_backend.model.security.UserConfig;
import com.nas_backend.service.AuthService;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemStatsService systemStatsService;
    private final AuthService authService;
    private final SystemAdminService systemAdminService;
    private final LogService logService;

    public SystemController(SystemStatsService systemStatsService, AuthService authService, SystemAdminService systemAdminService, LogService logService) {
        this.systemStatsService = systemStatsService;
        this.authService = authService;
        this.systemAdminService = systemAdminService;
        this.logService = logService;
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
    public ResponseEntity<SystemStatsResponse> getSystemStats(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        requireValidUser(authHeader);

        // Call the service to gather stats
        SystemStatsResponse stats = systemStatsService.getSystemStats();

        // Return the report using SystemStatsResponse DTO
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/reboot")
    public ResponseEntity<?> rebootSystem(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        requireValidUser(authHeader); 
        
        systemAdminService.rebootSystem();
        
        // Return "OK" immediately, before the machine shuts down
        return ResponseEntity.ok(Map.of("message", "System is rebooting now."));
    }

    @PostMapping("/shutdown")
    public ResponseEntity<?> shutdownSystem(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        requireValidUser(authHeader); 
        
        systemAdminService.shutdownSystem();
        
        // Return "OK" immediately, before the machine shuts down
        return ResponseEntity.ok(Map.of("message", "System is shutting down now."));
    }

    @GetMapping("/logs/events")
    public ResponseEntity<List<String>> getEventLogs(@RequestHeader(name = "Authorization", required = false) String authHeader) {

        requireValidUser(authHeader);

        // Read nas-events.log
        List<String> logs = logService.getLogFileContent("nas-events.log");
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/logs/transfers")
    public ResponseEntity<List<String>> getTransferLogs(@RequestHeader(name = "Authorization", required = false) String authHeader) {

        requireValidUser(authHeader);

        // Read nas-transfers.log
        List<String> logs = logService.getLogFileContent("nas-transfers.log");
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/logs/system")
    public ResponseEntity<List<String>> getSystemLogs(@RequestHeader(name = "Authorization", required = false) String authHeader) {

        requireValidUser(authHeader);

        // Read nas-system.log
        List<String> logs = logService.getLogFileContent("nas-system.log");
        return ResponseEntity.ok(logs);
    }
}
