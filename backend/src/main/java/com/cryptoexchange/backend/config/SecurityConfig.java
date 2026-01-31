package com.cryptoexchange.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the application.
 * Allows public access to Swagger UI, OpenAPI docs, and Actuator health endpoints.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(authorize -> authorize
				// Allow public access to Swagger UI and OpenAPI endpoints
				.requestMatchers(
					"/swagger-ui.html",
					"/swagger-ui/**",
					"/v3/api-docs/**",
					"/swagger-resources/**",
					"/webjars/**"
				).permitAll()
				// Allow public access to Actuator health endpoints
				.requestMatchers("/actuator/health/**").permitAll()
				// Allow public access to Actuator info endpoint
				.requestMatchers("/actuator/info").permitAll()
				// For development: allow all other requests (remove in production)
				.anyRequest().permitAll()
			)
			// Disable CSRF for development (enable in production with proper configuration)
			.csrf(csrf -> csrf.disable())
			// Disable HTTP Basic authentication popup
			.httpBasic(httpBasic -> httpBasic.disable());

		return http.build();
	}
}
