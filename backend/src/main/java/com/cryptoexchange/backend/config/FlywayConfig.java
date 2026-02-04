package com.cryptoexchange.backend.config;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Configuration to ensure Flyway runs migrations before Hibernate/JPA initialization.
 * This class provides diagnostic logging and ensures Flyway is properly configured.
 * Spring Boot should automatically run Flyway before JPA, but this provides
 * additional verification and logging.
 */
@Configuration
@Order(1) // Run before JPA configuration
public class FlywayConfig implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(FlywayConfig.class);

    @Autowired(required = false)
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @PostConstruct
    public void checkFlywayConfiguration() {
        logger.info("=== Flyway Configuration Check ===");
        if (flyway == null) {
            logger.error("Flyway bean is null! Flyway migrations will not run.");
            logger.error("Check that spring.flyway.enabled=true and Flyway dependencies are present.");
        } else {
            logger.info("✓ Flyway bean found");
            org.flywaydb.core.api.configuration.Configuration config = flyway.getConfiguration();
            logger.info("  - Locations: {}", java.util.Arrays.toString(config.getLocations()));
            logger.info("  - Baseline on migrate: {}", config.isBaselineOnMigrate());
            logger.info("  - Validate on migrate: {}", config.isValidateOnMigrate());
        }
        logger.info("===================================");
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // This runs after the application context is fully refreshed
        // Check if Flyway schema history table exists
        if (dataSource != null && flyway != null) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                // Check if flyway_schema_history table exists
                ResultSet rs = stmt.executeQuery(
                    "SELECT EXISTS (" +
                    "  SELECT FROM information_schema.tables " +
                    "  WHERE table_schema = 'public' " +
                    "  AND table_name = 'flyway_schema_history'" +
                    ")"
                );
                
                if (rs.next() && rs.getBoolean(1)) {
                    logger.info("✓ Flyway schema history table exists - migrations have run");
                    
                    // Check migration count
                    ResultSet countRs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM flyway_schema_history"
                    );
                    if (countRs.next()) {
                        logger.info("  - Applied migrations: {}", countRs.getInt(1));
                    }
                } else {
                    logger.warn("⚠ Flyway schema history table does NOT exist!");
                    logger.warn("  This indicates Flyway migrations may not have run.");
                }
            } catch (Exception e) {
                logger.error("Error checking Flyway schema history: {}", e.getMessage(), e);
            }
        }
    }
}
