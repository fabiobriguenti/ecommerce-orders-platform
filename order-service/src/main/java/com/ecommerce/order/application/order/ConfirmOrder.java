package com.ecommerce.order.application.order;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.ecommerce.order.application.exception.OrderNotFoundException;
import com.ecommerce.order.application.exception.ProductNotAvailableException;
import com.ecommerce.order.application.exception.ProductNotFoundException;
import com.ecommerce.order.domain.event.OrderConfirmed;
import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.order.OrderStatus;
import com.ecommerce.order.domain.port.CatalogPort;
import com.ecommerce.order.domain.port.DomainEventPublisherPort;
import com.ecommerce.order.domain.port.OrderRepositoryPort;
import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.domain.vo.ProductId;

/**
 * Confirms an order, capturing the current catalog price of every item at confirmation time and
 * freezing the total. Idempotent: confirming an already confirmed order returns it without
 * re-pricing or re-publishing the event.
 */
public class ConfirmOrder {

    private final OrderRepositoryPort orderRepository;
    private final CatalogPort catalogPort;
    private final DomainEventPublisherPort eventPublisher;

    public ConfirmOrder(OrderRepositoryPort orderRepository, CatalogPort catalogPort,
                        DomainEventPublisherPort eventPublisher) {
        this.orderRepository = orderRepository;
        this.catalogPort = catalogPort;
        this.eventPublisher = eventPublisher;
    }

    public Mono<Order> handle(UUID orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)))
                .flatMap(order -> order.status() == OrderStatus.CONFIRMED
                        ? Mono.just(order)
                        : confirmWithCurrentPrices(order));
    }

    private Mono<Order> confirmWithCurrentPrices(Order order) {
        return currentPrices(order)
                .flatMap(prices -> {
                    order.confirm(prices);
                    return orderRepository.save(order);
                })
                .flatMap(saved -> eventPublisher
                        .publish(new OrderConfirmed(saved.id(), saved.customerId(),
                                saved.total().orElseThrow(), Instant.now()))
                        .thenReturn(saved));
    }

    private Mono<Map<ProductId, Money>> currentPrices(Order order) {
        return Flux.fromIterable(order.items())
                .flatMap(item -> {
                    ProductId productId = item.productId();
                    return catalogPort.findById(productId)
                            .switchIfEmpty(Mono.error(new ProductNotFoundException(productId)))
                            .flatMap(product -> product.available()
                                    ? Mono.just(Map.entry(productId, product.price()))
                                    : Mono.error(new ProductNotAvailableException(productId)));
                })
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }
}
