package com.nas_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.net.URLDecoder;
import java.nio.file.Paths;

@SpringBootApplication
@EnableScheduling
public class NasBackendApplication {

	public static void main(String[] args) {
		String rootPath = getAppRootPath();

		String configPath = Paths.get(rootPath, "config").toAbsolutePath().toString();
		String dataPath = Paths.get(rootPath, "data").toAbsolutePath().toString();
		String logsPath = Paths.get(rootPath, "logs").toAbsolutePath().toString();

		// KROK 2: Wyświetl meldunek (to, o co prosiłeś)
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
            // Bierzemy URL, który jest bezpieczniejszy niż URI
            var location = NasBackendApplication.class.getProtectionDomain().getCodeSource().getLocation();
            String protocol = location.getProtocol();
            String path = URLDecoder.decode(location.getPath(), "UTF-8");

            if ("file".equals(protocol)) {
                // Scenariusz 1: Odpalamy z IDE.
                // Ścieżka to ".../backend/target/classes/"
                File file = new File(path);
                // Cofamy się o 2 poziomy: z 'classes' do 'target', z 'target' do 'backend'
                return file.getParentFile().getParentFile().getAbsolutePath();
            }

            if ("jar".equals(protocol)) {
                // Scenariusz 2: Odpalamy z pliku .jar.
                // Ścieżka to "file:/.../backend/target/app.jar!/BOOT-INF/classes!"
                // Musimy wyciąć ścieżkę do samego pliku JAR.
                String jarPath = path.substring(0, path.indexOf("!/"));
                
                // Czasem ścieżka może mieć jeszcze "file:" na początku
                if (jarPath.startsWith("file:")) {
                    jarPath = jarPath.substring(5);
                }

                File jarFile = new File(jarPath); // To jest ".../target/app.jar"
                // Cofamy się o 2 poziomy: z 'app.jar' do 'target', z 'target' do 'backend'
                return jarFile.getParentFile().getParentFile().getAbsolutePath();
            }

            throw new IllegalStateException("Unknown protocol: " + protocol);

        } catch (Exception e) {
            System.err.println("FATAL: Could not determine app root path, falling back to '.'. Error: " + e.getMessage());
            return ".";
        }
    }
}