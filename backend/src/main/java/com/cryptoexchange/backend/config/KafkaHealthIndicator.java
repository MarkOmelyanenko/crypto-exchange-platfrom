package com.cryptoexchange.backend.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Custom health indicator for Kafka.
 * Verifies connectivity by attempting to list topics.
 * 
 * Note: This indicator is only active when Kafka is configured.
 * If Kafka is unavailable, the health check will report DOWN,
 * but the application may still start if Kafka consumers/producers
 * are not immediately required.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaHealthIndicator extends AbstractHealthIndicator {

	private final String bootstrapServers;

	public KafkaHealthIndicator(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
		this.bootstrapServers = bootstrapServers;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) throws Exception {
		try {
			Properties props = new Properties();
			props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
			props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
			props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000);

			try (AdminClient adminClient = AdminClient.create(props)) {
				adminClient.listTopics(new ListTopicsOptions().timeoutMs(5000))
						.names()
						.get(5, TimeUnit.SECONDS);
				
				builder.up()
						.withDetail("bootstrapServers", bootstrapServers);
			}
		} catch (Exception e) {
			builder.down()
					.withDetail("error", e.getMessage())
					.withDetail("bootstrapServers", bootstrapServers);
		}
	}
}
