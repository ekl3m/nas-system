package com.nas_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.nio.file.Paths;

@SpringBootApplication
@EnableScheduling
public class NasBackendApplication {

	public static void main(String[] args) {
		String rootPath = getAppRootPath();

        try {
            Paths.get(rootPath, "data").toFile().mkdirs();
            Paths.get(rootPath, "logs").toFile().mkdirs();
            Paths.get(rootPath, "config").toFile().mkdirs();
        } catch (Exception e) {
            System.err.println("FATAL: Could not create necessary directories at root: " + rootPath);
            e.printStackTrace();
            return; // Do not start if this operation failed
        }

		System.setProperty("APP_ROOT_PATH", rootPath);
		SpringApplication.run(NasBackendApplication.class, args);
	}

	private static String getAppRootPath() {
		// Create a ghost file in current working directory
		File here = new File(".");
		try {
			// Find out its full canonical path
			String currentWorkingDirectory = here.getCanonicalPath();

			// Knowing that launch folder is target, revert one directory upwards to .../backend
			return new File(currentWorkingDirectory).getParent();

		} catch (Exception e) {
			System.err.println("FATAL: Could not determine app root path, falling back. " + e.getMessage());
			return ".";
		}
	}
}