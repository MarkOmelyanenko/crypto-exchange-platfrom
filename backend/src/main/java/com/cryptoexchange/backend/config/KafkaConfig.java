package com.cryptoexchange.backend.config;

import com.cryptoexchange.backend.domain.event.MarketTickEvent;
import com.cryptoexchange.backend.domain.event.MarketTradeEvent;
import com.cryptoexchange.backend.domain.event.OrderCreatedEvent;
import com.cryptoexchange.backend.domain.event.TradeExecutedEvent;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for order matching events.
 * Configures JSON serialization, topic management, and error handling with DLT.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.topics.orders:orders}")
    private String ordersTopic;

    @Value("${app.kafka.topics.trades:trades}")
    private String tradesTopic;

    @Value("${app.kafka.topics.orders-dlt:orders.DLT}")
    private String ordersDltTopic;

    @Value("${app.kafka.topics.trades-dlt:trades.DLT}")
    private String tradesDltTopic;

    @Value("${app.kafka.topics.market-ticks:market.ticks}")
    private String marketTicksTopic;

    @Value("${app.kafka.topics.market-trades:market.trades}")
    private String marketTradesTopic;

    // Topic configuration
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    public NewTopic ordersTopic() {
        // 1 partition for strict ordering (MVP safety)
        return new NewTopic(ordersTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic tradesTopic() {
        return new NewTopic(tradesTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic ordersDltTopic() {
        return new NewTopic(ordersDltTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic tradesDltTopic() {
        return new NewTopic(tradesDltTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic marketTicksTopic() {
        return new NewTopic(marketTicksTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic marketTradesTopic() {
        return new NewTopic(marketTradesTopic, 1, (short) 1);
    }

    // Producer configuration
    @Bean
    public ProducerFactory<String, OrderCreatedEvent> orderEventProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all"); // Wait for all replicas
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // Exactly-once semantics
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000); // 30 seconds
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public ProducerFactory<String, TradeExecutedEvent> tradeEventProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, OrderCreatedEvent> orderEventKafkaTemplate() {
        return new KafkaTemplate<>(orderEventProducerFactory());
    }

    @Bean
    public KafkaTemplate<String, TradeExecutedEvent> tradeEventKafkaTemplate() {
        return new KafkaTemplate<>(tradeEventProducerFactory());
    }

    @Bean
    public ProducerFactory<String, MarketTickEvent> marketTickEventProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public ProducerFactory<String, MarketTradeEvent> marketTradeEventProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, MarketTickEvent> marketTickEventKafkaTemplate() {
        return new KafkaTemplate<>(marketTickEventProducerFactory());
    }

    @Bean
    public KafkaTemplate<String, MarketTradeEvent> marketTradeEventKafkaTemplate() {
        return new KafkaTemplate<>(marketTradeEventProducerFactory());
    }

    // Consumer configuration
    @Bean
    public ConsumerFactory<String, OrderCreatedEvent> orderCreatedEventConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "matching-engine");
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // Trust only our events package
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.cryptoexchange.backend.domain.event");
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderCreatedEvent.class);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual acknowledgment
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> kafkaListenerContainerFactory(
            KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderCreatedEventConsumerFactory());
        factory.getContainerProperties().setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // Set concurrency to 1 for strict ordering (MVP safety)
        factory.setConcurrency(1);
        
        // Configure error handling with retry and DLT
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, ex) -> new org.apache.kafka.common.TopicPartition(ordersDltTopic, record.partition())
        );
        
        CommonErrorHandler errorHandler = new DefaultErrorHandler(
            recoverer,
            new FixedBackOff(1000L, 3) // Retry 3 times with 1 second delay
        );
        
        factory.setCommonErrorHandler(errorHandler);
        
        return factory;
    }
}
