package com.cryptoexchange.backend.domain.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Internal Spring ApplicationEvent published when a user's balance changes.
 * This event is published synchronously and triggers cache eviction after DB commit.
 */
public class DomainBalanceChanged extends ApplicationEvent {
    private final UUID userId;

    public DomainBalanceChanged(Object source, UUID userId) {
        super(source);
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
}
