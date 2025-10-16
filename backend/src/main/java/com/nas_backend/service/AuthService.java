package com.nas_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nas_backend.model.UserConfig;
import com.nas_backend.model.UserToken;

import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final String CONFIG_DIR = "config";
    private static final String USERS_FILE_NAME = "users.json";
    private static final String USERS_TEMPLATE_PATH = "users.json.template";

    private final Map<String, UserConfig> users = new ConcurrentHashMap<>(); // username -> UserConfig (full user data)
    private final Map<String, UserToken> activeTokens = new ConcurrentHashMap<>(); // token -> UserToken (login session)
    private final AppConfigService configService;

    private final ObjectMapper mapper = new ObjectMapper();

    public AuthService(AppConfigService configService) {
        this.configService = configService;
    }

    private long getTokenTtlSeconds() {
        return configService.getConfig().getServer().getTokenTTL();
    }

    @PostConstruct
    private void loadUsersFromConfig() {
        String rootPath = System.getProperty("APP_ROOT_PATH");
        File usersFile = Paths.get(rootPath, CONFIG_DIR, USERS_FILE_NAME).toFile();

        if (!usersFile.exists()) {
            System.out.println("users.json not found. Creating default from template...");
            try {
                createDefaultUsersFile(usersFile);
            } catch (IOException e) {
                throw new RuntimeException("FATAL: Could not create default users.json file.", e);
            }
        }

        try {
            List<UserConfig> userList = mapper.readValue(usersFile, new TypeReference<List<UserConfig>>() {});
            for (UserConfig u : userList) {
                users.put(u.getUsername(), u);
            }
            System.out.println("Users loaded successfully from " + usersFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("FATAL: Failed to load or parse users.json from " + usersFile.getAbsolutePath(), e);
        }
    }

    private void createDefaultUsersFile(File usersFile) throws IOException {
        usersFile.getParentFile().mkdirs();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(USERS_TEMPLATE_PATH)) {
            if (in == null) {
                throw new IOException("FATAL: users.json.template not found in resources!");
            }
            byte[] templateData = FileCopyUtils.copyToByteArray(in);
            FileCopyUtils.copy(templateData, usersFile);
            System.out.println("Default users file created at: " + usersFile.getAbsolutePath());
            throw new RuntimeException("Default users.json has been created. Please define at least one user before starting the application.");
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