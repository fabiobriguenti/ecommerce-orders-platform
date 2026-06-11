package com.ecommerce.order.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface OrderRepository extends ReactiveCrudRepository<OrderRow, UUID> {
}
