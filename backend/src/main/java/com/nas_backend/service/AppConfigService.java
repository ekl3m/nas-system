package com.nas_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nas_backend.model.AppConfig;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;

@Service
public class AppConfigService {

    private AppConfig config;

    @PostConstruct
    public void loadConfig() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.json")) {
            if (in != null) {
                config = mapper.readValue(in, AppConfig.class);
            } else {
                // fallback - file in directory near JAR
                config = mapper.readValue(new File("config.json"), AppConfig.class);
            }
        } catch (Exception e) {
            throw new RuntimeException("Cannot load config.json", e);
        }
    }

    public AppConfig getConfig() {
        return config;
    }
}
