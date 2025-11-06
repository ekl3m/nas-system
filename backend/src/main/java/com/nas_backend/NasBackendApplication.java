package com.nas_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.net.URL;

@SpringBootApplication
@EnableScheduling
public class NasBackendApplication {

	public static void main(String[] args) {
		System.setProperty("APP_ROOT_PATH", getAppRootPath());
		SpringApplication.run(NasBackendApplication.class, args);
	}

	private static String getAppRootPath() {
        try {
            // Grab URL location
            URL location = NasBackendApplication.class.getProtectionDomain().getCodeSource().getLocation();
            
            // Extract path from URL
            String path = location.getPath();

            // If path contains "!/", it means, that we are in a JAR file
            // Path looks like this: /.../nas-backend.jar!/BOOT-INF/classes!/
            // Everything after ".jar" has to be cut out
            if (path.contains("!/")) {
                path = path.substring(0, path.indexOf("!/"));
                
                // Sometimes path will start wih "file:"
                if (path.startsWith("file:")) {
                    path = path.substring(5);
                }
            }

            // Now path is either "/.../nas-backend.jar" or "/.../target/classes/"
            File source = new File(path);

            if (source.isDirectory()) {
                // Launch from IDE, source is ".../target/classes"
                // Go back up directory tree twice, to "backend" and return
                return source.getParentFile().getParentFile().getAbsolutePath();
            } else {
                // Launch form JAR, source is ".../nas-backend.jar"
                // Return the foder which contains .jar
                return source.getParentFile().getAbsolutePath();
            }

        } catch (Exception e) { 
            System.err.println("FATAL: Could not determine app root path, falling back to working directory. " + e.getMessage());
            return ".";
        }
    }
}