package com.nas_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nas_backend.model.UserConfig;
import com.nas_backend.model.UserToken;

import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private static final String CONFIG_DIR = "config";
    private static final String USERS_FILE_NAME = "users.json";
    private static final String USERS_TEMPLATE_PATH = "users.json.template";

    private final Map<String, UserConfig> users = new ConcurrentHashMap<>(); // username -> UserConfig (full user data)
    private final Map<String, UserToken> activeTokens = new ConcurrentHashMap<>(); // token -> UserToken (login session)
    private final AppConfigService configService;
    private final FileService fileService;

    private final ObjectMapper mapper = new ObjectMapper();

    public AuthService(AppConfigService configService, FileService fileService) {
        this.configService = configService;
        this.fileService = fileService;
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
                if (u.getUsername() == null || u.getUsername().startsWith(".")) {
                    // Critical error. System admin must correct users.json.
                    logger.error("FATAL: Invalid username found in users.json: '{}'. Usernames cannot be null or start with a dot.", u.getUsername());
                    throw new RuntimeException("Invalid username in users.json. Usernames cannot be null or start with a dot.");
                }
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

        // Ensure user's virtual root directory exists
        try {
            fileService.createVirtualPath(username);
            logger.info("Verified virtual root for user: {}", username);
        } catch (Exception e) {
            logger.error("Failed to create/verify root virtual folder for user: {}", username, e);
        }

        // Ensure user's physical root directory exists
        try {
            ensureUserStoragePaths(username);
        } catch (IOException e) {
            logger.warn("Could not create all physical storage paths for user: {}", username, e);
        }

        // Log out previous sessions
        activeTokens.values().removeIf(t -> t.getUsername().equals(username));

        String token = UUID.randomUUID().toString();
        activeTokens.put(token, new UserToken(username, token, getTokenTtlSeconds()));
        return token;
    }

    private void ensureUserStoragePaths(String username) throws IOException {
        List<String> storagePaths = configService.getConfig().getStorage().getPaths();
        if (storagePaths == null || storagePaths.isEmpty()) {
            logger.warn("No storage paths configured in config.json! Cannot create physical user folders.");
            return;
        }

        logger.info("Verifying physical storage paths for user: {}", username);
        for (String drivePath : storagePaths) {
            Path userPhysicalPath = Paths.get(drivePath, username);
            if (Files.notExists(userPhysicalPath)) {
                try {
                    Files.createDirectories(userPhysicalPath);
                    logger.info("Created missing physical path: {}", userPhysicalPath);
                } catch (IOException e) {
                    logger.error("Failed to create physical path at: {}", userPhysicalPath, e);
                }
            }
        }
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