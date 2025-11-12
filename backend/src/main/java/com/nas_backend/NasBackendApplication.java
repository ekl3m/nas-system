package com.nas_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.nio.file.Paths;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class NasBackendApplication {

	public static void main(String[] args) {
        ApplicationHome home = new ApplicationHome(NasBackendApplication.class);

        // Returns the folder where the application is running from
        // - Raspberry : .../backend/target/
        // - IDE: .../backend/target/classes/
        File appFolder = home.getDir();

        // Determine root path based on environment
        String rootPath;
        if (appFolder.getAbsolutePath().endsWith(File.separator + "classes")) {
            // Launched from IDE (in 'target/classes/'). Move up 2 levels: from 'classes' to 'backend'
            rootPath = appFolder.getParentFile().getParentFile().getAbsolutePath();
        } else {
            // Running on Raspberry Pi (in 'target/'). Move up 1 level: from 'target' to 'backend'
            rootPath = appFolder.getParentFile().getAbsolutePath();
        }
        
		String configPath = Paths.get(rootPath, "config").toAbsolutePath().toString();
		String dataPath = Paths.get(rootPath, "data").toAbsolutePath().toString();
		String logsPath = Paths.get(rootPath, "logs").toAbsolutePath().toString();

		System.out.println("===================================================================");
		System.out.println("NAS SYSTEM STARTING UP...");
		System.out.println("APP ROOT PATH (APP_ROOT_PATH) : " + rootPath);
		System.out.println("CONFIG DIRECTORY : " + configPath);
		System.out.println("DATA DIRECTORY   : " + dataPath);
		System.out.println("LOGS DIRECTORY   : " + logsPath);
		System.out.println("===================================================================");

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
}