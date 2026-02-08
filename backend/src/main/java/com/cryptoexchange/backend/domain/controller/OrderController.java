package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.Order;
import com.cryptoexchange.backend.domain.model.OrderSide;
import com.cryptoexchange.backend.domain.model.OrderStatus;
import com.cryptoexchange.backend.domain.model.OrderType;
import com.cryptoexchange.backend.domain.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST controller for limit order management.
 * 
 * <p>Provides endpoints for creating, querying, and canceling limit orders.
 * Orders are placed on markets and require sufficient balance in the appropriate
 * currency (quote currency for BUY orders, base currency for SELL orders).
 * 
 * <p>All endpoints require authentication. User ID is passed as a request parameter.
 */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Order management endpoints")
@Validated
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Create order", description = "Creates a new order for the authenticated user")
    public ResponseEntity<OrderResponse> createOrder(@RequestParam UUID userId, @Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.placeOrder(
            userId,
            request.marketId,
            request.side,
            request.type,
            request.price,
            request.quantity
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID", description = "Returns an order by ID for the authenticated user")
    public ResponseEntity<OrderResponse> getOrder(@RequestParam UUID userId, @PathVariable UUID id) {
        Order order = orderService.getMyOrder(id, userId);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping
    @Operation(summary = "List my orders", description = "Returns paginated list of orders for the authenticated user")
    public ResponseEntity<Page<OrderResponse>> listOrders(
            @RequestParam UUID userId,
            @RequestParam(required = false) OrderStatus status,
            Pageable pageable) {
        Page<Order> orders = orderService.listMyOrders(userId, status, pageable);
        Page<OrderResponse> response = orders.map(OrderResponse::from);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel order", description = "Cancels an order for the authenticated user")
    public ResponseEntity<CancelOrderResponse> cancelOrder(@RequestParam UUID userId, @PathVariable UUID id) {
        Order order = orderService.cancelMyOrder(id, userId);
        return ResponseEntity.ok(CancelOrderResponse.from(order));
    }

    // DTOs
    public static class CreateOrderRequest {
        @NotNull(message = "Market ID is required")
        public UUID marketId;

        @NotNull(message = "Side is required")
        public OrderSide side;

        @NotNull(message = "Type is required")
        public OrderType type;

        @DecimalMin(value = "0.00000001", message = "Price must be positive")
        public BigDecimal price;

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.00000001", message = "Quantity must be positive")
        public BigDecimal quantity;

        @AssertTrue(message = "Price is required for LIMIT orders")
        public boolean isPriceValidForOrderType() {
            if (type == null) {
                return true; // Type validation will catch this
            }
            if (type == OrderType.LIMIT) {
                return price != null && price.compareTo(BigDecimal.ZERO) > 0;
            }
            return true; // MARKET orders don't require price
        }
    }

    public static class OrderResponse {
        public UUID id;
        public UUID marketId;
        public String marketSymbol;
        public String baseAssetSymbol;
        public String quoteAssetSymbol;
        public OrderSide side;
        public OrderType type;
        public OrderStatus status;
        public BigDecimal price;
        public BigDecimal quantity;
        public BigDecimal filledQuantity;
        public java.time.OffsetDateTime createdAt;
        public java.time.OffsetDateTime updatedAt;

        public static OrderResponse from(Order order) {
            OrderResponse response = new OrderResponse();
            response.id = order.getId();
            response.marketId = order.getMarket().getId();
            response.marketSymbol = order.getMarket().getSymbol();
            response.baseAssetSymbol = order.getMarket().getBaseAsset().getSymbol();
            response.quoteAssetSymbol = order.getMarket().getQuoteAsset().getSymbol();
            response.side = order.getSide();
            response.type = order.getType();
            response.status = order.getStatus();
            response.price = order.getPrice();
            response.quantity = order.getAmount();
            response.filledQuantity = order.getFilledAmount();
            response.createdAt = order.getCreatedAt();
            response.updatedAt = order.getUpdatedAt();
            return response;
        }
    }

    public static class CancelOrderResponse {
        public UUID id;
        public OrderStatus status;

        public static CancelOrderResponse from(Order order) {
            CancelOrderResponse response = new CancelOrderResponse();
            response.id = order.getId();
            response.status = order.getStatus();
            return response;
        }
    }
}
