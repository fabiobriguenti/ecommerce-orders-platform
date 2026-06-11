package com.ecommerce.order.domain.port;

import reactor.core.publisher.Mono;

import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.domain.vo.ProductId;

/**
 * Outbound port to the Catalog service (simulated by WireMock from Phase 5). An empty result
 * means the product does not exist.
 */
public interface CatalogPort {

    Mono<ProductView> findById(ProductId id);

    record ProductView(ProductId id, boolean available, Money price) {
    }
}
