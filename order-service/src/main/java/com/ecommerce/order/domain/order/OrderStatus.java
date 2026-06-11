package com.ecommerce.order.domain.order;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle (ADR-10). Allowed transitions:
 *
 * <pre>
 * CREATED          -> CONFIRMED, CANCELLED
 * CONFIRMED        -> AWAITING_PAYMENT, CANCELLED
 * AWAITING_PAYMENT -> PAID, PAYMENT_REJECTED, CANCELLED
 * PAYMENT_REJECTED -> AWAITING_PAYMENT (retry), CANCELLED
 * PAID             -> (terminal)
 * CANCELLED        -> (terminal)
 * </pre>
 */
public enum OrderStatus {

    CREATED,
    CONFIRMED,
    AWAITING_PAYMENT,
    PAYMENT_REJECTED,
    PAID,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(CREATED, EnumSet.of(CONFIRMED, CANCELLED));
        ALLOWED.put(CONFIRMED, EnumSet.of(AWAITING_PAYMENT, CANCELLED));
        ALLOWED.put(AWAITING_PAYMENT, EnumSet.of(PAID, PAYMENT_REJECTED, CANCELLED));
        ALLOWED.put(PAYMENT_REJECTED, EnumSet.of(AWAITING_PAYMENT, CANCELLED));
        ALLOWED.put(PAID, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public boolean acceptsItems() {
        return this == CREATED;
    }

    public boolean isTerminal() {
        return this == PAID || this == CANCELLED;
    }
}
