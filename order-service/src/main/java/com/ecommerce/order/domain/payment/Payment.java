package com.ecommerce.order.domain.payment;

import java.util.UUID;

import com.ecommerce.order.domain.exception.InvalidPaymentStateException;
import com.ecommerce.order.domain.exception.MaxPaymentAttemptsReachedException;
import com.ecommerce.order.domain.vo.Money;

/**
 * Payment aggregate (ADR-09). Tracks the gateway attempts (max 3) for a single order. The
 * order's cancellation after the 3rd rejection is orchestrated by the use case, which reads
 * {@link #hasReachedMaxAttempts()} after {@link #reject()}.
 *
 * <p>{@code attempts} counts how many gateway calls have been made: {@link #initiate} is attempt 1,
 * each {@link #retry()} bumps it by one.
 */
public class Payment {

    public static final int MAX_ATTEMPTS = 3;

    private final UUID id;
    private final UUID orderId;
    private final Money amount;
    private PaymentStatus status;
    private int attempts;

    private Payment(UUID id, UUID orderId, Money amount, PaymentStatus status, int attempts) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.attempts = attempts;
    }

    /** Starts a payment for a confirmed order: first attempt, in PROCESSING. */
    public static Payment initiate(UUID id, UUID orderId, Money amount) {
        return new Payment(id, orderId, amount, PaymentStatus.PROCESSING, 1);
    }

    /** Rebuilds a payment from persistence. */
    public static Payment reconstitute(UUID id, UUID orderId, Money amount, PaymentStatus status, int attempts) {
        return new Payment(id, orderId, amount, status, attempts);
    }

    /** Approves the current attempt. Idempotent: approving an approved payment is a no-op. */
    public void approve() {
        if (status == PaymentStatus.APPROVED) {
            return;
        }
        if (status != PaymentStatus.PROCESSING) {
            throw new InvalidPaymentStateException("approve", status);
        }
        status = PaymentStatus.APPROVED;
    }

    /** Rejects the current attempt. Idempotent: rejecting a rejected payment does not double-count. */
    public void reject() {
        if (status == PaymentStatus.REJECTED) {
            return;
        }
        if (status != PaymentStatus.PROCESSING) {
            throw new InvalidPaymentStateException("reject", status);
        }
        status = PaymentStatus.REJECTED;
    }

    /** Starts a new attempt after a rejection, up to {@link #MAX_ATTEMPTS}. */
    public void retry() {
        if (status != PaymentStatus.REJECTED) {
            throw new InvalidPaymentStateException("retry", status);
        }
        if (attempts >= MAX_ATTEMPTS) {
            throw new MaxPaymentAttemptsReachedException(orderId, attempts);
        }
        attempts++;
        status = PaymentStatus.PROCESSING;
    }

    public boolean hasReachedMaxAttempts() {
        return attempts >= MAX_ATTEMPTS;
    }

    public boolean isApproved() {
        return status == PaymentStatus.APPROVED;
    }

    public UUID id() {
        return id;
    }

    public UUID orderId() {
        return orderId;
    }

    public Money amount() {
        return amount;
    }

    public PaymentStatus status() {
        return status;
    }

    public int attempts() {
        return attempts;
    }
}
