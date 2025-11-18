package com.nas_backend.service.system;

import com.nas_backend.model.config.AppConfig;
import com.nas_backend.service.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

@Service
public class SystemStartupService {

    private static final Logger logger = LoggerFactory.getLogger(SystemStartupService.class);

    private final EmailService emailService;
    private final AppConfigService configService;

    public SystemStartupService(EmailService emailService, AppConfigService configService) {
        this.emailService = emailService;
        this.configService = configService;
    }

    // This method will be called when the application is fully started
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        AppConfig config = configService.getConfig();
        String hostname = config.getServer().getHostname();

        // Get Java info 
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        String jvmName = runtimeBean.getVmName();
        String jvmVersion = runtimeBean.getVmVersion();

        logger.info("SYSTEM STARTUP: Sending notification email...");

        emailService.sendSystemSuccessEmail(
            "System '" + hostname + "' is ONLINE and ready.\n" +
            "JVM: " + jvmName + " (" + jvmVersion + ")\n" +
            "Network services: ACTIVE"
        );
    }
}