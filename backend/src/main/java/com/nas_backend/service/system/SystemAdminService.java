package com.nas_backend.service.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SystemAdminService {

    private static final Logger logger = LoggerFactory.getLogger(SystemAdminService.class);
    private final ShellService shellService;
    private final EmailService emailService;

    public SystemAdminService(ShellService shellService, EmailService emailService) {
        this.shellService = shellService;
        this.emailService = emailService;
    }

    // Asynchronously executes the reboot command
    public void rebootSystem() {
        logger.warn("SYSTEM ADMIN: Received reboot command! Rebooting in 3 seconds...");
        emailService.sendSystemSuccessEmail("System reboot initiated by administrator via API.");

        // Launch in a new thread to avoid blocking the HTTP response
        new Thread(() -> {
            try {
                Thread.sleep(3000); // Give 3 seconds to send the HTTP response
                String result = shellService.executeCommand("sudo /sbin/reboot");

                if (result == null) {
                    // Shell service returned null, exit code != 0
                    logger.error("SYSTEM ADMIN: Reboot command failed (exit code error).");
                    emailService.sendSystemErrorEmail(
                        "CRITICAL: System reboot failed to execute.\n" +
                        "The command 'sudo /sbin/reboot' returned an error code.\n" +
                        "Check server logs manually.",
                        "System"
                    );
                }
            } catch (Exception e) {
                logger.error("SYSTEM ADMIN: Reboot thread failed!", e);
                emailService.sendSystemErrorEmail(
                    "CRITICAL: System reboot thread crashed.\n" +
                    "Error: " + e.getMessage(),
                    "System"
                );
            }
        }).start();
    }

    // Asynchronously executes the shutdown command
    public void shutdownSystem() {
        logger.warn("SYSTEM ADMIN: Received shutdown command! Shutting down in 3 seconds...");
        emailService.sendSystemSuccessEmail("System shutdown initiated by administrator via API.");

        // Launch in a new thread to avoid blocking the HTTP response
        new Thread(() -> {
            try {
                Thread.sleep(3000); // Give 3 seconds to send the HTTP response
                String result = shellService.executeCommand("sudo /sbin/shutdown -h now");

                if (result == null) {
                    // Shell service returned null, exit code != 0
                    logger.error("SYSTEM ADMIN: Shutdown command failed (exit code error).");
                    emailService.sendSystemErrorEmail(
                        "CRITICAL: System shutdown failed to execute.\n" +
                        "The command 'sudo /sbin/shutdown -h now' returned an error code.\n" +
                        "Check server logs manually.",
                        "System"
                    );
                }
            } catch (Exception e) {
                logger.error("SYSTEM ADMIN: Shutdown thread failed!", e);
                emailService.sendSystemErrorEmail(
                    "CRITICAL: System shutdown thread crashed.\n" +
                    "Error: " + e.getMessage(),
                    "System"
                );
            }
        }).start();
    }
}
