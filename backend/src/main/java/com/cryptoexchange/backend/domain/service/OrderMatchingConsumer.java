package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer for order matching.
 * Listens to OrderCreatedEvent and triggers matching engine.
 * 
 * Uses concurrency=1 per partition to ensure sequential matching per market,
 * reducing concurrency issues.
 */
@Component
public class OrderMatchingConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderMatchingConsumer.class);

    private final MatchingEngine matchingEngine;

    public OrderMatchingConsumer(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    @KafkaListener(
        topics = "orders",
        groupId = "matching-engine",
        concurrency = "1",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCreated(
            @Payload OrderCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        try {
            log.debug("Received OrderCreatedEvent: {}", event);
            
            UUID orderId = event.getOrderId();
            if (orderId == null) {
                log.error("OrderCreatedEvent has null orderId, skipping");
                acknowledgment.acknowledge();
                return;
            }

            // Trigger matching engine
            matchingEngine.matchOrder(orderId);
            
            // Acknowledge message after successful processing
            acknowledgment.acknowledge();
            
            log.debug("Successfully processed OrderCreatedEvent for order {}", orderId);
        } catch (Exception e) {
            log.error("Error processing OrderCreatedEvent: {}", event, e);
            // In production, you might want to implement retry logic or dead letter queue
            // For now, we acknowledge to avoid blocking the queue
            acknowledgment.acknowledge();
        }
    }
}
