package com.cryptoexchange.backend;

import com.cryptoexchange.backend.config.DotenvConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(BackendApplication.class);
		// Load .env file from project root into Spring environment
		app.addInitializers(context -> {
			ConfigurableEnvironment env = context.getEnvironment();
			DotenvConfig.loadDotenv(env);
		});
		app.run(args);
	}

}
