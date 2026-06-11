package com.ecommerce.order.infrastructure.persistence;

import java.time.Instant;

import com.ecommerce.order.domain.payment.Payment;
import com.ecommerce.order.domain.payment.PaymentStatus;
import com.ecommerce.order.domain.vo.Money;

final class PaymentPersistenceMapper {

    private PaymentPersistenceMapper() {
    }

    static PaymentRow toRow(Payment payment) {
        PaymentRow row = new PaymentRow();
        row.setId(payment.id());
        row.setOrderId(payment.orderId());
        row.setAmount(payment.amount().amount());
        row.setCurrency(payment.amount().currency());
        row.setStatus(payment.status().name());
        row.setAttempts(payment.attempts());
        row.setVersion(payment.version());
        Instant now = Instant.now();
        row.setCreatedAt(now); // @InsertOnlyProperty: ignored on update
        row.setUpdatedAt(now);
        return row;
    }

    static Payment toDomain(PaymentRow row) {
        return Payment.reconstitute(row.getId(), row.getOrderId(),
                Money.of(row.getAmount(), row.getCurrency()),
                PaymentStatus.valueOf(row.getStatus()), row.getAttempts(), row.getVersion());
    }
}
