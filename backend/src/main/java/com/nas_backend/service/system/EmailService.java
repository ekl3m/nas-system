package com.nas_backend.service.system;

import com.nas_backend.model.config.AppConfig;
import com.nas_backend.service.AppConfigService;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final AppConfigService configService;

    // Injected from application.properties
    @Value("${spring.mail.username}")
    private String fromEmail;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss z");
    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Warsaw"); // Set timezone to Warsaw

    public EmailService(JavaMailSender mailSender, AppConfigService configService) {
        this.mailSender = mailSender;
        this.configService = configService;
    }

    // Parsing helper to get formatted timestamp
    private String getFormattedTimestamp() {
        return DATE_FORMATTER.withZone(ZONE_ID).format(Instant.now());
    }

    // Main email sending engine
    @Async
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        if (!configService.getConfig().getServer().isEnableEmailNotifications()) return;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = HTML enabled

            mailSender.send(message);
            logger.info("HTML Email sent successfully to: {}", to);
        } catch (Exception e) {
            logger.error("Failed to send HTML email to {}", to, e);
        }
    }

    // Error message email builder
    public void sendSystemErrorEmail(String errorDetails, String user) {
        AppConfig config = configService.getConfig();
        String adminEmail = config.getServer().getAdminEmail();
        if (adminEmail == null || adminEmail.isBlank())
            return;

        String subject = "[" + config.getServer().getHostname() + "] A critical error has been reported";

        // Use LinkedHashMap to maintain insertion order
        Map<String, String> details = new LinkedHashMap<>();
        details.put("User", (user != null ? user : "N/A"));
        details.put("Time", getFormattedTimestamp());
        details.put("Error Details", errorDetails);

        String body = buildHtmlTemplate("System Alert: Critical Error", "A critical error occurred that requires your attention.", details);

        sendHtmlEmail(adminEmail, subject, body);
    }

    // Success message email builder
    public void sendSystemSuccessEmail(String eventDetails) {
        AppConfig config = configService.getConfig();
        String adminEmail = config.getServer().getAdminEmail();
        if (adminEmail == null || adminEmail.isBlank())
            return;

        String subject = "[" + config.getServer().getHostname() + "] Event completed successfully";


        // Use LinkedHashMap to maintain insertion order
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Event", eventDetails);
        details.put("Time", getFormattedTimestamp());

        String body = buildHtmlTemplate("System Notification", "A system event has completed successfully.", details);

        sendHtmlEmail(adminEmail, subject, body);
    }

    // HTML email template builder
    private String buildHtmlTemplate(String title, String subtitle, Map<String, String> details) {
        StringBuilder content = new StringBuilder();
        content.append("<table style='width: 100%; border-collapse: collapse;'>");

        for (Map.Entry<String, String> entry : details.entrySet()) {
            content.append("<tr>");
            content.append(
                    "<td style='padding: 8px; border: 1px solid #ddd; background-color: #f9f9f9; width: 150px;'><strong>")
                    .append(entry.getKey()).append(":</strong></td>");

            if (entry.getKey().toLowerCase().contains("error")) {
                content.append(
                        "<td style='padding: 8px; border: 1px solid #ddd;'><pre style='background-color:#f0f0f0; border:1px solid #ccc; padding:10px; margin:0; white-space: pre-wrap; word-wrap: break-word;'>")
                        .append(entry.getValue())
                        .append("</pre></td>");
            } else {
                content.append("<td style='padding: 8px; border: 1px solid #ddd;'>")
                        .append(entry.getValue())
                        .append("</td>");
            }
            content.append("</tr>");
        }
        content.append("</table>");

        return "<!DOCTYPE html>" +
                "<html lang='en'>" +
                "<head><style>" +
                "body {font-family: Arial, sans-serif; line-height: 1.6; color: #333;}" +
                ".container {width: 90%; max-width: 600px; margin: 20px auto; padding: 20px; border: 1px solid #ddd; border-radius: 5px; box-shadow: 0 2px 5px rgba(0,0,0,0.1);}"
                +
                ".header {font-size: 24px; color: " + (title.contains("Error") ? "#d9534f" : "#337ab7")
                + "; border-bottom: 2px solid #eee; padding-bottom: 10px;}" +
                ".content {margin-top: 20px;}" +
                "pre {white-space: pre-wrap; word-wrap: break-word;}" +
                ".footer {margin-top: 20px; font-size:12px; color:#888; border-top: 1px solid #eee; padding-top: 10px;}"
                +
                "</style></head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" + title + "</div>" +
                "<p><em>" + subtitle + "</em></p>" +
                "<div class='content'>" + content.toString() + "</div>" +
                "<div class='footer'>This is an automated message from your NAS.</div>" +
                "</div>" +
                "</body></html>";
    }
}