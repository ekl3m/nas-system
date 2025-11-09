package com.nas_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.net.URLDecoder;
import java.nio.file.Paths;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class NasBackendApplication {

	public static void main(String[] args) {
		String rootPath = getAppRootPath();

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

	private static String getAppRootPath() {
        try {
            var location = NasBackendApplication.class.getProtectionDomain().getCodeSource().getLocation();
            String protocol = location.getProtocol();
            String path = URLDecoder.decode(location.getPath(), "UTF-8");

            if ("file".equals(protocol)) {
                // IDE launch
                File file = new File(path);
                // Go back up two times in directory tree
                return file.getParentFile().getParentFile().getAbsolutePath();
            }

            if ("jar".equals(protocol)) {
                // JAR launch
                // Path example: "file:/.../backend/target/app.jar!/BOOT-INF/classes!"
                // Cut out jar path
                String jarPath = path.substring(0, path.indexOf("!/"));
                
                // Path sometimes can have "file:" in the beginning
                if (jarPath.startsWith("file:")) {
                    jarPath = jarPath.substring(5);
                }

                File jarFile = new File(jarPath); // This is ".../target/app.jar"
				// Go back up two times in directory tree
                return jarFile.getParentFile().getParentFile().getAbsolutePath();
            }

            throw new IllegalStateException("Unknown protocol: " + protocol);

        } catch (Exception e) {
            System.err.println("FATAL: Could not determine app root path, falling back to '.'. Error: " + e.getMessage());
            return ".";
        }
    }
}