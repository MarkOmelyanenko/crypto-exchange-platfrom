package com.cryptoexchange.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI cryptoExchangeOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("Crypto Exchange Simulator API")
						.description("REST API for the Crypto Exchange Simulator application. " +
								"Provides endpoints for order management, wallet operations, market data, and trade history.")
						.version("1.0.0")
						.contact(new Contact()
								.name("Crypto Exchange Simulator")
								.email("support@cryptoexchange.com")))
				.components(new Components()
						.addSecuritySchemes("bearer-jwt", new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("JWT token authentication (currently disabled for development)")))
				.addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
	}

	@Bean
	public GroupedOpenApi publicApi() {
		return GroupedOpenApi.builder()
				.group("public")
				.pathsToMatch("/api/**")
				.build();
	}
}
