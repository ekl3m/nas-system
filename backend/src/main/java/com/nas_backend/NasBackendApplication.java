package com.nas_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.net.URISyntaxException;

@SpringBootApplication
@EnableScheduling
public class NasBackendApplication {

	public static void main(String[] args) {
		System.setProperty("APP_ROOT_PATH", getAppRootPath());
		SpringApplication.run(NasBackendApplication.class, args);
	}

	private static String getAppRootPath() {
		try {
			File source = new File(
					NasBackendApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			String path = source.getAbsolutePath();

			if (path.endsWith(File.separator + "classes")) {
				// IDE Run: go up two levels to get to the module root ("backend")
				return new File(path).getParentFile().getParentFile().getAbsolutePath();
			} else {
				// JAR Run: get the directory containing the JAR
				return source.getParent();
			}
		} catch (URISyntaxException e) {
			System.err.println("Could not determine app root path, falling back to working directory. " + e.getMessage());
			return ".";
		}
	}
}