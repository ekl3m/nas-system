package com.nas_backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final Map<String, String> users = new ConcurrentHashMap<>();
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    public AuthService() {
        // Sample users
        users.put("admin", "admin");
        users.put("tata", "tata123");
    }

    public String login(String username, String password) {
        String expectedPassword = users.get(username);
        if (expectedPassword == null || !expectedPassword.equals(password)) {
            throw new RuntimeException("Invalid username or password");
        }

        String token = UUID.randomUUID().toString();
        tokens.put(token, username);
        return token;
    }

    public String getUsernameFromToken(String token) {
        return tokens.get(token);
    }

    public String extractToken(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }
}