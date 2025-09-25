package com.nas_backend.controller;

import com.nas_backend.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody AuthRequest authRequest) {
        String token = authService.login(authRequest.username(), authRequest.password());
        return Map.of("token", token);
    }

    // Login DTO
    public record AuthRequest(String username, String password) {
    }
}