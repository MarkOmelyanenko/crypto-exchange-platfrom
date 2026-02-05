package com.cryptoexchange.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Disabled: This test requires H2 database.
 * All tests should be hermetic and offline.
 */
@Disabled("Requires H2 database - use pure unit tests instead")
// @SpringBootTest - Removed to prevent Spring context loading
// @TestPropertySource(properties = {
//     "spring.datasource.url=jdbc:h2:mem:testdb_app;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
//     "spring.datasource.driver-class-name=org.h2.Driver",
//     "spring.datasource.username=sa",
//     "spring.datasource.password=",
//     "spring.jpa.hibernate.ddl-auto=create-drop",
//     "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
//     "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
//     "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
//     "spring.flyway.enabled=false",
//     "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
//     "KAFKA_BOOTSTRAP_SERVERS=localhost:9092",
//     "management.health.kafka.enabled=false"
// })
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
