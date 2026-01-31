package com.cryptoexchange.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Configuration to load .env file from project root (parent directory).
 * This allows Spring Boot to read environment variables from .env file.
 */
@Configuration
public class DotenvConfig {

	public static void loadDotenv(ConfigurableEnvironment environment) {
		// Look for .env in project root (parent directory from backend/)
		File envFile = new File("../.env");
		if (!envFile.exists()) {
			// Fallback: try current directory
			envFile = new File(".env");
		}
		
		if (envFile.exists() && envFile.isFile()) {
			Map<String, Object> envProperties = new HashMap<>();
			try (Stream<String> lines = Files.lines(Paths.get(envFile.getAbsolutePath()))) {
				lines.filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("#"))
					.forEach(line -> {
						int equalsIndex = line.indexOf('=');
						if (equalsIndex > 0) {
							String key = line.substring(0, equalsIndex).trim();
							String value = line.substring(equalsIndex + 1).trim();
							// Remove quotes if present
							if (value.startsWith("\"") && value.endsWith("\"")) {
								value = value.substring(1, value.length() - 1);
							} else if (value.startsWith("'") && value.endsWith("'")) {
								value = value.substring(1, value.length() - 1);
							}
							envProperties.put(key, value);
						}
					});
				
				MapPropertySource propertySource = new MapPropertySource("dotenv", envProperties);
				environment.getPropertySources().addFirst(propertySource);
			} catch (IOException e) {
				System.err.println("Warning: Could not load .env file: " + e.getMessage());
			}
		}
	}
}
