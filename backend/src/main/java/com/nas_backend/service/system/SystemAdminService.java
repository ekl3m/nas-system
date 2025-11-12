package com.nas_backend.service.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SystemAdminService {

    private static final Logger logger = LoggerFactory.getLogger(SystemAdminService.class);
    private final ShellService shellService;

    public SystemAdminService(ShellService shellService) {
        this.shellService = shellService;
    }

    // Asynchronously executes the reboot command
    public void rebootSystem() {
        logger.warn("SYSTEM ADMIN: Received reboot command! Rebooting in 3 seconds...");
        // Launch in a new thread to avoid blocking the HTTP response
        new Thread(() -> {
            try {
                Thread.sleep(3000); // Give 3 seconds to send the HTTP response
                shellService.executeCommand("sudo /sbin/reboot");
            } catch (Exception e) {
                logger.error("SYSTEM ADMIN: Reboot command failed!", e);
            }
        }).start();
    }

    // Asynchronously executes the shutdown command
    public void shutdownSystem() {
        logger.warn("SYSTEM ADMIN: Received shutdown command! Shutting down in 3 seconds...");
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                shellService.executeCommand("sudo /sbin/shutdown -h now");
            } catch (Exception e) {
                logger.error("SYSTEM ADMIN: Shutdown command failed!", e);
            }
        }).start();
    }
}
