package com.nas_backend.controller;

import com.nas_backend.service.AuthService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.*;
import io.swagger.v3.oas.annotations.Operation;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for user authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user and return an UUID token")
    public Map<String, String> login(@RequestBody AuthRequest authRequest) {
        String token = authService.login(authRequest.username(), authRequest.password());
        return Map.of("token", token);
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Invalidate the user's UUID token")
    public ResponseEntity<Void> logout(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        String token = authService.extractToken(authHeader);
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }

    // Login DTO
    public record AuthRequest(String username, String password) {
    }
}