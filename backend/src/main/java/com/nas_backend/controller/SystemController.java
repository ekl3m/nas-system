package com.nas_backend.controller;

import com.nas_backend.model.SystemStatsResponse;
import com.nas_backend.model.UserConfig;
import com.nas_backend.service.AuthService;
import com.nas_backend.service.SystemStatsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemStatsService systemStatsService;
    private final AuthService authService;

    public SystemController(SystemStatsService systemStatsService, AuthService authService) {
        this.systemStatsService = systemStatsService;
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

    @GetMapping("/stats")
    public ResponseEntity<SystemStatsResponse> getSystemStats(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        requireValidUser(authHeader);

        // Call the service to gather stats
        SystemStatsResponse stats = systemStatsService.getSystemStats();

        // Return the report using SystemStatsResponse DTO
        return ResponseEntity.ok(stats);
    }
}
