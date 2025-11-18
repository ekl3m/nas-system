package com.nas_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nas_backend.model.entity.UserToken;
import com.nas_backend.repository.UserTokenRepository;
import com.nas_backend.model.security.UserConfig;
import com.nas_backend.service.file.FileService;
import com.nas_backend.service.system.EmailService;
import com.nas_backend.service.system.LogService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final AppConfigService configService;
    private final FileService fileService;
    private final LogService logService;
    private final EmailService emailService;
    private final UserTokenRepository userTokenRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    public AuthService(AppConfigService configService, FileService fileService, LogService logService, EmailService emailService, UserTokenRepository userTokenRepository) {
        this.configService = configService;
        this.fileService = fileService;
        this.logService = logService;
        this.emailService = emailService;
        this.userTokenRepository = userTokenRepository;
    }

    private long getTokenTtlSeconds() {
        return configService.getConfig().getServer().getTokenTTL();
    }

    @PostConstruct
    private void loadUsersFromConfig() {
        String rootPath = System.getProperty("APP_ROOT_PATH");
        File usersFile = Paths.get(rootPath, CONFIG_DIR, USERS_FILE_NAME).toFile();

        if (!usersFile.exists()) {
            logger.warn("users.json not found. Creating default from template...");
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
                    // Critical error. System admin must correct users.json
                    String msg = "FATAL: Invalid username found in users.json: '" + u.getUsername() + "'. Usernames cannot be null or start with a dot.";
                    logger.error(msg);
                    logService.logSystemEvent(msg);
                    throw new RuntimeException("Invalid username in users.json. Usernames cannot be null or start with a dot.");
                }
                users.put(u.getUsername(), u);
            }

            logger.info("Users loaded successfully from " + usersFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("FATAL: Failed to load or parse users.json from " + usersFile.getAbsolutePath(), e);
        }
    }

    // Service methods

    @Transactional
    public String login(String username, String password) {
        UserConfig user = users.get(username);
        if (user == null || !user.getPassword().equals(password)) {
            logService.logSystemEvent("SECURITY ALERT: Failed login attempt for user: '" + username + "'");
            throw new RuntimeException("Invalid username or password");
        }

        // Make sure user virtual root and physical storage paths exist
        try {
            fileService.createVirtualPath(username);
            logger.info("Verified virtual root for user: {}", username);
        } catch (Exception e) {
            logger.error("Failed to create/verify root virtual folder for user: {}", username, e);
            emailService.sendSystemErrorEmail("Login Error: Failed to create virtual root for user " + username + " \nError: " + e.getMessage(), username);
        }
        try {
            ensureUserStoragePaths(username);
        } catch (IOException e) {
            logger.warn("Could not create all physical storage paths for user: {}", username, e);
            emailService.sendSystemErrorEmail("Login Warning: Failed to create physical storage paths for user " + username + " \nError: " + e.getMessage(), username);
        }
        
        // Clear out old tokens if any exist
        List<UserToken> oldTokens = userTokenRepository.findByUsername(username);
        if (!oldTokens.isEmpty()) {
            logger.info("Auth: Clearing {} old, stale tokens for user: {}", oldTokens.size(), username);
            userTokenRepository.deleteAll(oldTokens);
        }

        // Create a new token
        String tokenString = UUID.randomUUID().toString();
        UserToken newToken = new UserToken(username, tokenString, getTokenTtlSeconds());
        
        // Save token to DB
        userTokenRepository.save(newToken);

        logService.logSystemEvent("User '" + username + "' logged in successfully. Session started.");
        return tokenString;
    }

    @Transactional
    public void logout(String token) {
        if (token != null) {
            // Store username for logging purposes
            Optional<UserToken> tokenOpt = userTokenRepository.findByToken(token);
            String username = tokenOpt.map(UserToken::getUsername).orElse("Unknown");

            // Simply remove from the database (token is @Id)
            userTokenRepository.deleteById(token);

            logService.logSystemEvent("User '" + username + "' logged out.");
        }
    }

    @Transactional
    public UserConfig getUserFromToken(String token) {
        if (token == null) {
            return null;
        }

        // Find in the database
        Optional<UserToken> userTokenOpt = userTokenRepository.findByToken(token);

        if (userTokenOpt.isEmpty()) {
            logger.warn("Auth: Invalid token presented: {}", token);
            return null; // Did not find user using this token
        }

        UserToken userToken = userTokenOpt.get();

        // Check whether token is expired
        if (userToken.isExpired()) {
            logger.warn("Auth: User presented expired token. Deleting from DB.");
            userTokenRepository.delete(userToken); // If token is expired, remove from DB
            return null;
        }

        // If token is valid, return the user
        return users.get(userToken.getUsername());
    }

    public String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }

    // Helper methods

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
                    logService.logSystemEvent("System created physical user directory at: " + userPhysicalPath);
                } catch (IOException e) {
                    logger.error("Failed to create physical path at: {}", userPhysicalPath, e);
                    throw e; // Rethrow to inform caller
                }
            }
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
            logger.info("Default users file created at: " + usersFile.getAbsolutePath());
            throw new RuntimeException("Default users.json has been created. Please define at least one user before starting the application.");
        }
    }
}