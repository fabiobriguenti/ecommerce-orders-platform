package com.ecommerce.order.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import com.ecommerce.order.domain.payment.Payment;
import com.ecommerce.order.domain.port.PaymentRepositoryPort;

@Repository
public class PaymentRepositoryAdapter implements PaymentRepositoryPort {

    private final R2dbcEntityTemplate template;

    public PaymentRepositoryAdapter(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Override
    public Mono<Payment> save(Payment payment) {
        PaymentRow row = PaymentPersistenceMapper.toRow(payment);
        Mono<PaymentRow> persisted = payment.version() == null ? template.insert(row) : template.update(row);
        return persisted.map(saved -> {
            payment.assignVersion(saved.getVersion());
            return payment;
        });
    }

    @Override
    public Mono<Payment> findById(UUID id) {
        return template.select(PaymentRow.class)
                .matching(Query.query(Criteria.where("id").is(id)))
                .one()
                .map(PaymentPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Payment> findByOrderId(UUID orderId) {
        return template.select(PaymentRow.class)
                .matching(Query.query(Criteria.where("order_id").is(orderId)))
                .one()
                .map(PaymentPersistenceMapper::toDomain);
    }
}
