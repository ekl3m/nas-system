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
		try {
			File source = new File(NasBackendApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			String path = source.getAbsolutePath();

			// Scenario A: IDE launch
			if (path.endsWith(File.separator + "classes")) {
				// Go back up two directories
				return new File(path).getParentFile().getParentFile().getAbsolutePath();
			}

			// Scenario B: JAR file launch
			if (path.endsWith(".jar")) {
				// Go back up two directories
				return new File(path).getParentFile().getParentFile().getAbsolutePath();
			}

			// Scenariusz awaryjny (np. dziwne testy)
			return source.getParentFile().getAbsolutePath();

		} catch (Exception e) {
			System.err.println("FATAL: Could not determine app root path, falling back to working directory. " + e.getMessage());
			return ".";
		}
	}
}