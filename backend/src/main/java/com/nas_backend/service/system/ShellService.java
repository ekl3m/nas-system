package com.nas_backend.service.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class ShellService {

    private static final Logger logger = LoggerFactory.getLogger(ShellService.class);

    // Powerful method to execute shell commands and get their output
    public String executeCommand(String command) {
        try {
            // Split command into parts for ProcessBuilder
            String[] commandParts = command.split(" ");

            // Use the robust ProcessBuilder
            ProcessBuilder pb = new ProcessBuilder(commandParts);
            pb.redirectErrorStream(true); // Combine error stream (stderr) with output (stdout)
            Process process = pb.start(); // Start the process

            StringBuilder output = new StringBuilder();

            // Try-with-resources automatically closes the reader
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;

                // Read all output lines
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // Wait for the command to actually finish and get its exit code
            int exitVal = process.waitFor();

            // Check the exit code. '0' is the universal sign for "SUCCESS" in Linux/Unix
            if (exitVal == 0) {
                return output.toString().trim();
            } else {
                // If the exit code is anything else (e.g., 1, 127), it means the command failed
                logger.warn("Command '{}' failed with exit code: {}", command, exitVal);
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to execute command: {}", command, e);
            return null;
        }
    }
}