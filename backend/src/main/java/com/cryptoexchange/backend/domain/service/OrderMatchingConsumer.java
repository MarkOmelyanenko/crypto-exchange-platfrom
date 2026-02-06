package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.event.OrderCreatedEvent;
import com.cryptoexchange.backend.domain.model.Order;
import com.cryptoexchange.backend.domain.model.OrderStatus;
import com.cryptoexchange.backend.domain.repository.OrderRepository;
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
 * Uses concurrency=1 per partition to ensure sequential matching per market.
 * Implements idempotency by checking order status before processing.
 * Error handling with retry and DLT is configured in KafkaConfig.
 * Only active when Kafka is configured.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class OrderMatchingConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderMatchingConsumer.class);

    private final MatchingEngine matchingEngine;
    private final OrderRepository orderRepository;

    public OrderMatchingConsumer(
            MatchingEngine matchingEngine,
            OrderRepository orderRepository) {
        this.matchingEngine = matchingEngine;
        this.orderRepository = orderRepository;
    }

    @KafkaListener(
        topics = "${app.kafka.topics.orders:orders}",
        groupId = "matching-engine",
        concurrency = "1",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCreated(
            @Payload OrderCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        
        UUID orderId = event.orderId();
        String marketSymbol = event.marketSymbol();
        
        log.debug("Received OrderCreatedEvent: orderId={}, marketSymbol={}, key={}", 
            orderId, marketSymbol, key);
        
        try {
            // Idempotency check: load order and verify it can be matched
            Order order = orderRepository.findById(orderId).orElse(null);
            
            if (order == null) {
                log.warn("Order {} not found in database, skipping matching", orderId);
                acknowledgment.acknowledge();
                return;
            }
            
            // Check if order is in a state that can be matched (idempotency)
            if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PARTIALLY_FILLED) {
                log.debug("Order {} has status {}, skipping matching (already processed or cancelled)", 
                    orderId, order.getStatus());
                acknowledgment.acknowledge();
                return;
            }
            
            // Trigger matching engine (transactional, handles order+balance+trade updates atomically)
            matchingEngine.matchOrder(orderId);
            
            // Acknowledge message after successful processing
            acknowledgment.acknowledge();
            
            log.debug("Successfully processed OrderCreatedEvent for order {} (market: {})", 
                orderId, marketSymbol);
                
        } catch (Exception e) {
            log.error("Error processing OrderCreatedEvent: orderId={}, marketSymbol={}, error={}", 
                orderId, marketSymbol, e.getMessage(), e);
            // Error handler in KafkaConfig will retry and send to DLT if retries exhausted
            // Re-throw to trigger error handler
            throw new RuntimeException("Failed to process OrderCreatedEvent for order " + orderId, e);
        }
    }
}
