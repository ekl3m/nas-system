package com.nas_backend.config;

import com.nas_backend.service.AppConfigService;
import com.nas_backend.service.system.monitor.MockSystemMonitor;
import com.nas_backend.service.system.monitor.RaspberrySystemMonitor;
import com.nas_backend.service.system.monitor.SystemMonitor;
import com.nas_backend.service.system.ShellService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MonitorConfig {

    private static final Logger logger = LoggerFactory.getLogger(MonitorConfig.class);

    // Factory method to create the appropriate SystemMonitor implementation
    @Bean
    public SystemMonitor systemMonitor(AppConfigService configService, ShellService shellService) {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();

        logger.info("Detecting OS... Name: '{}', Arch: '{}'", osName, osArch);

        // Check if it is definitely a Raspberry Pi
        if (osName.contains("linux") && (osArch.contains("arm") || osArch.contains("aarch64"))) {
            logger.info("OS detected as Raspberry Pi (Linux ARM/AArch64). Loading RaspberrySystemMonitor.");
            return new RaspberrySystemMonitor(configService, shellService);
        } else {
            // In all other cases (Mac, Windows) load the mock monitor
            logger.warn("OS is not Linux ARM. Loading MockSystemMonitor for development.");
            return new MockSystemMonitor(configService); // <-- Zmienimy Mocka, żeby też brał config
        }
    }
}