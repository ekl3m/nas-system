package com.nas_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nas_backend.model.AppConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;

@Service
public class AppConfigService {

    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE_NAME = "config.json";
    private static final String CONFIG_TEMPLATE_PATH = "config.json.template";

    private AppConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void loadConfig() {
        String rootPath = System.getProperty("APP_ROOT_PATH");
        File configFile = Paths.get(rootPath, CONFIG_DIR, CONFIG_FILE_NAME).toFile();

        if (!configFile.exists()) {
            System.out.println("Config file not found. Creating default from template...");
            try {
                createDefaultConfig(configFile);
            } catch (IOException e) {
                throw new RuntimeException("FATAL: Could not create default config file.", e);
            }
        }

        try {
            config = mapper.readValue(configFile, AppConfig.class);
            System.out.println("Configuration loaded successfully from " + configFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("FATAL: Cannot load config.json from " + configFile.getAbsolutePath(), e);
        }
    }

    private void createDefaultConfig(File configFile) throws IOException {
        configFile.getParentFile().mkdirs();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_TEMPLATE_PATH)) {
            if (in == null) {
                throw new IOException("FATAL: config.json.template not found in resources!");
            }
            byte[] fileData = FileCopyUtils.copyToByteArray(in);
            FileCopyUtils.copy(fileData, configFile);
            System.out.println("Default config created at: " + configFile.getAbsolutePath());
            throw new RuntimeException("Default configuration has been created. Please edit it before first use.");
        }
    }

    public AppConfig getConfig() {
        return config;
    }
}