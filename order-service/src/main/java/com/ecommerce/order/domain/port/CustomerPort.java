package com.ecommerce.order.domain.port;

import reactor.core.publisher.Mono;

import com.ecommerce.order.domain.vo.CustomerId;

/**
 * Outbound port to the Customer service (simulated by WireMock from Phase 5). An empty result
 * means the customer does not exist.
 */
public interface CustomerPort {

    Mono<CustomerView> findById(CustomerId id);

    record CustomerView(CustomerId id, boolean active) {
    }
}
