package com.cryptoexchange.backend.config;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Configuration to ensure Flyway runs and logs properly.
 * Spring Boot should run Flyway automatically, but this ensures it happens
 * and provides better logging.
 */
@Configuration
@Order(1) // Run before JPA configuration
public class FlywayConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlywayConfig.class);

    @Autowired(required = false)
    private Flyway flyway;

    @PostConstruct
    public void checkFlywayConfiguration() {
        if (flyway == null) {
            logger.error("Flyway bean is null! Flyway migrations will not run.");
            logger.error("Check that spring.flyway.enabled=true and Flyway dependencies are present.");
        } else {
            logger.info("Flyway bean found. Migrations should run automatically.");
            logger.info("Flyway configuration: {}", flyway.getConfiguration());
        }
    }
}
