package com.ecommerce.order.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.payment.Payment;
import com.ecommerce.order.domain.payment.PaymentStatus;
import com.ecommerce.order.domain.vo.CustomerId;
import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.domain.vo.ProductId;
import com.ecommerce.order.domain.vo.Quantity;

class PaymentRepositoryAdapterIT extends AbstractPostgresIT {

    private UUID persistConfirmedOrder() {
        OrderRepositoryAdapter orders = new OrderRepositoryAdapter(template);
        Order order = Order.create(UUID.randomUUID(), CustomerId.of("cust-pay"));
        order.addItem(ProductId.of("p1"), Quantity.of(1));
        order.confirm(Map.of(ProductId.of("p1"), Money.of("10.00", "BRL")));
        order.awaitPayment();
        orders.save(order).block();
        return order.id();
    }

    @Test
    void savesAndReloadsPaymentByIdAndOrderId() {
        PaymentRepositoryAdapter adapter = new PaymentRepositoryAdapter(template);
        UUID orderId = persistConfirmedOrder();
        Payment payment = Payment.initiate(UUID.randomUUID(), orderId, Money.of("10.00", "BRL"));

        StepVerifier.create(adapter.save(payment)
                        .then(adapter.findByOrderId(orderId)))
                .assertNext(loaded -> {
                    assertThat(loaded.status()).isEqualTo(PaymentStatus.PROCESSING);
                    assertThat(loaded.attempts()).isEqualTo(1);
                    assertThat(loaded.version()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void updatesPaymentAcrossAttempts() {
        PaymentRepositoryAdapter adapter = new PaymentRepositoryAdapter(template);
        UUID orderId = persistConfirmedOrder();
        Payment payment = Payment.initiate(UUID.randomUUID(), orderId, Money.of("10.00", "BRL"));
        adapter.save(payment).block();

        payment.reject();
        payment.retry();
        StepVerifier.create(adapter.save(payment)
                        .then(adapter.findById(payment.id())))
                .assertNext(loaded -> {
                    assertThat(loaded.status()).isEqualTo(PaymentStatus.PROCESSING);
                    assertThat(loaded.attempts()).isEqualTo(2);
                    assertThat(loaded.version()).isEqualTo(1L);
                })
                .verifyComplete();
    }
}
