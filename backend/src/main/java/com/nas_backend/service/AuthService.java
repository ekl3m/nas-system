package com.nas_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nas_backend.model.UserConfig;
import com.nas_backend.model.UserToken;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    // username -> UserConfig (full user data)
    private final Map<String, UserConfig> users = new ConcurrentHashMap<>();

    // token -> UserToken (login session)
    private final Map<String, UserToken> activeTokens = new ConcurrentHashMap<>();

    private final AppConfigService configService;

    public AuthService(AppConfigService configService) {
        this.configService = configService;
    }

    private long getTokenTtlSeconds() {
        return configService.getConfig().getTokenTTL();
    }

    @PostConstruct
    private void loadUsersFromConfig() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("users.json")) {
            if (in == null) {
                throw new RuntimeException("users.json not found in resources");
            }

            List<UserConfig> userList = mapper.readValue(in, new TypeReference<List<UserConfig>>() {
            });
            for (UserConfig u : userList) {
                users.put(u.getUsername(), u);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load users.json", e);
        }
    }

    public String login(String username, String password) {
        UserConfig user = users.get(username);
        if (user == null || !user.getPassword().equals(password)) {
            throw new RuntimeException("Invalid username or password");
        }

        // Log out previous sessions
        activeTokens.values().removeIf(t -> t.getUsername().equals(username));

        String token = UUID.randomUUID().toString();
        activeTokens.put(token, new UserToken(username, token, getTokenTtlSeconds()));
        return token;
    }

    public void logout(String token) {
        if (token != null) {
            activeTokens.remove(token);
        }
    }

    public UserConfig getUserFromToken(String token) {
        UserToken userToken = activeTokens.get(token);
        if (userToken == null) {
            return null;
        }
        if (userToken.isExpired()) {
            activeTokens.remove(token);
            return null;
        }
        return users.get(userToken.getUsername());
    }

    public String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }
}