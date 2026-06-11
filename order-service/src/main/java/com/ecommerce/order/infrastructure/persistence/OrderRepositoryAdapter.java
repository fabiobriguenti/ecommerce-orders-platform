package com.ecommerce.order.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.port.OrderRepositoryPort;
import com.ecommerce.order.domain.vo.CustomerId;

/**
 * R2DBC adapter for the order aggregate. Persists the header row plus its items atomically and
 * propagates the optimistic-locking version back to the in-memory aggregate so sequential saves
 * within one use case work correctly (ADR-05).
 */
@Repository
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final R2dbcEntityTemplate template;

    public OrderRepositoryAdapter(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Override
    @Transactional
    public Mono<Order> save(Order order) {
        OrderRow row = OrderPersistenceMapper.toRow(order);
        Mono<OrderRow> persisted = order.version() == null ? template.insert(row) : template.update(row);
        return persisted.flatMap(saved -> replaceItems(order)
                .then(Mono.fromCallable(() -> {
                    order.assignVersion(saved.getVersion());
                    return order;
                })));
    }

    private Mono<Void> replaceItems(Order order) {
        return template.delete(OrderItemRow.class)
                .matching(Query.query(Criteria.where("order_id").is(order.id())))
                .all()
                .thenMany(Flux.fromIterable(OrderPersistenceMapper.toItemRows(order)).flatMap(template::insert))
                .then();
    }

    @Override
    public Mono<Order> findById(UUID id) {
        return template.select(OrderRow.class)
                .matching(Query.query(Criteria.where("id").is(id)))
                .one()
                .flatMap(row -> loadItems(id).map(items -> OrderPersistenceMapper.toDomain(row, items)));
    }

    @Override
    public Flux<Order> findByCustomerId(CustomerId customerId) {
        return template.select(OrderRow.class)
                .matching(Query.query(Criteria.where("customer_id").is(customerId.value())))
                .all()
                .flatMap(row -> loadItems(row.getId()).map(items -> OrderPersistenceMapper.toDomain(row, items)));
    }

    @Override
    public Mono<Boolean> existsActiveByCustomerId(CustomerId customerId) {
        return template.select(OrderRow.class)
                .matching(Query.query(Criteria.where("customer_id").is(customerId.value())
                        .and("status").notIn("PAID", "CANCELLED")))
                .count()
                .map(count -> count > 0);
    }

    private Mono<List<OrderItemRow>> loadItems(UUID orderId) {
        return template.select(OrderItemRow.class)
                .matching(Query.query(Criteria.where("order_id").is(orderId)))
                .all()
                .collectList();
    }
}
