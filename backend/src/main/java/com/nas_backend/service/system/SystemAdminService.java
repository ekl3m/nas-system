package com.nas_backend.service.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SystemAdminService {

    private static final Logger logger = LoggerFactory.getLogger(SystemAdminService.class);
    private final ShellService shellService;
    private final EmailService emailService;
    private final LogService logService;

    public SystemAdminService(ShellService shellService, EmailService emailService, LogService logService) {
        this.shellService = shellService;
        this.emailService = emailService;
        this.logService = logService;
    }

    // Asynchronously executes the reboot command
    public void rebootSystem() {
        logger.warn("SYSTEM ADMIN: Received reboot command! Rebooting in 3 seconds...");
        logService.logSystemEvent("System reboot initiated by administrator.");
        emailService.sendSystemSuccessEmail("System reboot initiated by administrator via API.");


        // Launch in a new thread to avoid blocking the HTTP response
        new Thread(() -> {
            try {
                Thread.sleep(3000); // Give 3 seconds to send the HTTP response
                String result = shellService.executeCommand("sudo /sbin/reboot");

                if (result == null) {
                    // Shell service returned null, exit code != 0
                    String errorMsg = "CRITICAL: System reboot failed to execute (exit code error).";
                    logger.error(errorMsg);
                    logService.logSystemEvent(errorMsg);
                    emailService.sendSystemErrorEmail(
                        "CRITICAL: System reboot failed to execute.\n" +
                        "The command 'sudo /sbin/reboot' returned an error code.\n" +
                        "Check server logs manually.",
                        "System"
                    );
                }
            } catch (Exception e) {
                String errorMsg = "CRITICAL: System reboot thread crashed.";
                logger.error(errorMsg, e);
                logService.logSystemEvent(errorMsg + " Error: " + e.getMessage());
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
        logService.logSystemEvent("System shutdown initiated by administrator.");
        emailService.sendSystemSuccessEmail("System shutdown initiated by administrator via API.");

        // Launch in a new thread to avoid blocking the HTTP response
        new Thread(() -> {
            try {
                Thread.sleep(3000); // Give 3 seconds to send the HTTP response
                String result = shellService.executeCommand("sudo /sbin/shutdown -h now");

                if (result == null) {
                    // Shell service returned null, exit code != 0
                    String errorMsg = "CRITICAL: System shutdown failed to execute (exit code error).";
                    logger.error(errorMsg);
                    logService.logSystemEvent(errorMsg);
                    emailService.sendSystemErrorEmail(
                        "CRITICAL: System shutdown failed to execute.\n" +
                        "The command 'sudo /sbin/shutdown -h now' returned an error code.\n" +
                        "Check server logs manually.",
                        "System"
                    );
                }
            } catch (Exception e) {
                String errorMsg = "CRITICAL: System shutdown thread crashed.";
                logger.error(errorMsg, e);
                logService.logSystemEvent(errorMsg + " Error: " + e.getMessage());
                emailService.sendSystemErrorEmail(
                    "CRITICAL: System shutdown thread crashed.\n" +
                    "Error: " + e.getMessage(),
                    "System"
                );
            }
        }).start();
    }
}
