package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.event.DomainOrderCreated;
import com.cryptoexchange.backend.domain.event.DomainTradeExecuted;
import com.cryptoexchange.backend.domain.event.OrderCreatedEvent;
import com.cryptoexchange.backend.domain.event.TradeExecutedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes domain events to Kafka AFTER database transaction commit.
 * This ensures events are only published if the transaction succeeds.
 * Only active when Kafka is configured.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, OrderCreatedEvent> orderEventTemplate;
    private final KafkaTemplate<String, TradeExecutedEvent> tradeEventTemplate;
    private final String ordersTopic;
    private final String tradesTopic;

    public KafkaEventPublisher(
            KafkaTemplate<String, OrderCreatedEvent> orderEventTemplate,
            KafkaTemplate<String, TradeExecutedEvent> tradeEventTemplate,
            @Value("${app.kafka.topics.orders}") String ordersTopic,
            @Value("${app.kafka.topics.trades}") String tradesTopic) {
        this.orderEventTemplate = orderEventTemplate;
        this.tradeEventTemplate = tradeEventTemplate;
        this.ordersTopic = ordersTopic;
        this.tradesTopic = tradesTopic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(DomainOrderCreated event) {
        try {
            OrderCreatedEvent kafkaEvent = new OrderCreatedEvent(
                event.getOrderId(),
                event.getMarketSymbol(),
                event.getSide(),
                java.time.Instant.now()
            );
            
            // Key by marketSymbol for partition affinity
            orderEventTemplate.send(ordersTopic, event.getMarketSymbol(), kafkaEvent);
            log.debug("Published OrderCreatedEvent to Kafka for order {} (market: {})", 
                event.getOrderId(), event.getMarketSymbol());
        } catch (Exception e) {
            log.error("Failed to publish OrderCreatedEvent to Kafka for order {}: {}", 
                event.getOrderId(), e.getMessage(), e);
            // Don't throw - event publishing failure shouldn't break the transaction
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTradeExecuted(DomainTradeExecuted event) {
        try {
            TradeExecutedEvent kafkaEvent = new TradeExecutedEvent(
                event.getTradeId(),
                event.getMarketSymbol(),
                event.getPrice(),
                event.getQuantity(),
                event.getExecutedAt()
            );
            
            // Key by marketSymbol for partition affinity
            tradeEventTemplate.send(tradesTopic, event.getMarketSymbol(), kafkaEvent);
            log.debug("Published TradeExecutedEvent to Kafka for trade {} (market: {})", 
                event.getTradeId(), event.getMarketSymbol());
        } catch (Exception e) {
            log.error("Failed to publish TradeExecutedEvent to Kafka for trade {}: {}", 
                event.getTradeId(), e.getMessage(), e);
            // Don't throw - event publishing failure shouldn't break the transaction
        }
    }
}
