package com.cryptoexchange.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Security configuration for the application.
 * Allows public access to Swagger UI, OpenAPI docs, and Actuator health endpoints.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			// Disable CSRF for stateless JWT authentication
			.csrf(csrf -> csrf.disable())
			// Enable CORS for frontend access
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			// Stateless session management
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				// Allow public access to auth endpoints
				.requestMatchers("/api/auth/**").permitAll()
				// Allow public access to system health (for monitoring and dashboard)
				.requestMatchers("/api/system/health").permitAll()
				// Allow public access to price endpoints (read-only market data)
				.requestMatchers("/api/prices/**").permitAll()
				// Allow public access to SSE price stream (read-only market data)
				.requestMatchers("/api/stream/**").permitAll()
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
				.requestMatchers("/actuator/info").permitAll()
				.requestMatchers("/api/system/health").permitAll()
				.requestMatchers("/api/prices/**").permitAll()
				// All other requests require authentication
				.anyRequest().authenticated()
			)
			// Return 401 JSON for unauthenticated requests (instead of 403 or redirect)
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint((request, response, authException) -> {
					response.setContentType(MediaType.APPLICATION_JSON_VALUE);
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					new ObjectMapper().writeValue(response.getOutputStream(),
						Map.of("code", "UNAUTHORIZED",
							   "message", "Authentication required",
							   "path", request.getRequestURI()));
				})
			)
			// Add JWT filter before UsernamePasswordAuthenticationFilter
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
			// Disable HTTP Basic authentication popup
			.httpBasic(httpBasic -> httpBasic.disable());

		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.of("http://localhost:5173"));
		configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setAllowCredentials(true);
		configuration.setExposedHeaders(List.of("Authorization"));
		
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
