package com.cryptoexchange.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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
 * Spring Security configuration for JWT-based authentication.
 * 
 * <p>Configures:
 * <ul>
 *   <li>Stateless JWT authentication (no sessions)</li>
 *   <li>CORS settings for frontend access</li>
 *   <li>Public endpoints: auth, health, prices, Swagger UI, Actuator</li>
 *   <li>All other endpoints require JWT authentication</li>
 *   <li>CSRF disabled (stateless API)</li>
 * </ul>
 * 
 * <p>CORS allowed origins are configured via {@code CORS_ALLOWED_ORIGINS} environment variable
 * (comma-separated, defaults to {@code http://localhost:5173}).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	@Value("${CORS_ALLOWED_ORIGINS:http://localhost:5173}")
	private String corsAllowedOrigins;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
	}

	/**
	 * Configures the security filter chain with JWT authentication.
	 * 
	 * <p>Public endpoints (no authentication required):
	 * <ul>
	 *   <li>{@code /api/auth/**} - Authentication endpoints</li>
	 *   <li>{@code /api/system/health} - System health check</li>
	 *   <li>{@code /api/prices/**} - Price data (read-only)</li>
	 *   <li>{@code /api/stream/**} - Price stream (SSE, read-only)</li>
	 *   <li>{@code /swagger-ui/**}, {@code /v3/api-docs/**} - API documentation</li>
	 *   <li>{@code /actuator/health/**}, {@code /actuator/info} - Monitoring endpoints</li>
	 * </ul>
	 * 
	 * <p>All other endpoints require valid JWT token in Authorization header.
	 * Unauthenticated requests receive 401 JSON response (not redirect).
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/api/auth/**").permitAll()
				.requestMatchers("/api/system/health").permitAll()
				.requestMatchers("/api/prices/**").permitAll()
				.requestMatchers("/api/stream/**").permitAll()
				.requestMatchers(
					"/swagger-ui.html",
					"/swagger-ui/**",
					"/v3/api-docs/**",
					"/swagger-resources/**",
					"/webjars/**"
				).permitAll()
				.requestMatchers("/actuator/health/**").permitAll()
				.requestMatchers("/actuator/info").permitAll()
				.anyRequest().authenticated()
			)
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
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
			.httpBasic(httpBasic -> httpBasic.disable());

		return http.build();
	}

	/**
	 * Configures CORS to allow frontend access.
	 * 
	 * <p>Origins are parsed from {@code CORS_ALLOWED_ORIGINS} env var (comma-separated).
	 * Defaults to {@code http://localhost:5173} if not set.
	 * 
	 * <p>Allows all HTTP methods and headers, with credentials enabled.
	 * Exposes Authorization header for JWT tokens.
	 */
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		List<String> allowedOrigins = Arrays.asList(corsAllowedOrigins.split("\\s*,\\s*"));
		configuration.setAllowedOrigins(allowedOrigins);
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
