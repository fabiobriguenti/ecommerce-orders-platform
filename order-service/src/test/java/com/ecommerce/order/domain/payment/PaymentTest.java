package com.ecommerce.order.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ecommerce.order.domain.exception.InvalidPaymentStateException;
import com.ecommerce.order.domain.exception.MaxPaymentAttemptsReachedException;
import com.ecommerce.order.domain.vo.Money;

class PaymentTest {

    private Payment initiate() {
        return Payment.initiate(UUID.randomUUID(), UUID.randomUUID(), Money.of("10.00", "BRL"));
    }

    @Test
    void initiatesProcessingOnFirstAttempt() {
        Payment payment = initiate();
        assertThat(payment.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payment.attempts()).isEqualTo(1);
    }

    @Test
    void approveTransitionsToApprovedAndIsIdempotent() {
        Payment payment = initiate();
        payment.approve();
        payment.approve();
        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.isApproved()).isTrue();
    }

    @Test
    void cannotRejectAfterApproval() {
        Payment payment = initiate();
        payment.approve();
        assertThatThrownBy(payment::reject).isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void rejectIsIdempotentAndDoesNotDoubleCount() {
        Payment payment = initiate();
        payment.reject();
        payment.reject();
        assertThat(payment.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(payment.attempts()).isEqualTo(1);
    }

    @Test
    void retryStartsNewProcessingAttempt() {
        Payment payment = initiate();
        payment.reject();
        payment.retry();
        assertThat(payment.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payment.attempts()).isEqualTo(2);
    }

    @Test
    void cannotRetryWhileProcessing() {
        Payment payment = initiate();
        assertThatThrownBy(payment::retry).isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void threeRejectionsReachMaxAttemptsThenNoFurtherRetry() {
        Payment payment = initiate();
        payment.reject();                 // attempt 1 rejected
        assertThat(payment.hasReachedMaxAttempts()).isFalse();
        payment.retry();                  // attempt 2
        payment.reject();
        assertThat(payment.hasReachedMaxAttempts()).isFalse();
        payment.retry();                  // attempt 3
        payment.reject();
        assertThat(payment.attempts()).isEqualTo(3);
        assertThat(payment.hasReachedMaxAttempts()).isTrue();
        assertThatThrownBy(payment::retry).isInstanceOf(MaxPaymentAttemptsReachedException.class);
    }
}
