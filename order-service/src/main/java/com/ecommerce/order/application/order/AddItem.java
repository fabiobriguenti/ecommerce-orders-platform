package com.ecommerce.order.application.order;

import java.util.UUID;

import reactor.core.publisher.Mono;

import com.ecommerce.order.application.exception.OrderNotFoundException;
import com.ecommerce.order.application.exception.ProductNotAvailableException;
import com.ecommerce.order.application.exception.ProductNotFoundException;
import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.port.CatalogPort;
import com.ecommerce.order.domain.port.OrderRepositoryPort;
import com.ecommerce.order.domain.vo.ProductId;
import com.ecommerce.order.domain.vo.Quantity;

/**
 * Adds an item to an open order after checking the product exists and is available in the catalog.
 * The aggregate enforces the increment-on-duplicate and immutability rules.
 */
public class AddItem {

    private final OrderRepositoryPort orderRepository;
    private final CatalogPort catalogPort;

    public AddItem(OrderRepositoryPort orderRepository, CatalogPort catalogPort) {
        this.orderRepository = orderRepository;
        this.catalogPort = catalogPort;
    }

    public Mono<Order> handle(Command command) {
        return Mono.defer(() -> {
            ProductId productId = ProductId.of(command.productId());
            Quantity quantity = Quantity.of(command.quantity());
            return orderRepository.findById(command.orderId())
                    .switchIfEmpty(Mono.error(new OrderNotFoundException(command.orderId())))
                    .flatMap(order -> catalogPort.findById(productId)
                            .switchIfEmpty(Mono.error(new ProductNotFoundException(productId)))
                            .flatMap(product -> product.available()
                                    ? Mono.just(order)
                                    : Mono.error(new ProductNotAvailableException(productId))))
                    .flatMap(order -> {
                        order.addItem(productId, quantity);
                        return orderRepository.save(order);
                    });
        });
    }

    public record Command(UUID orderId, String productId, int quantity) {
    }
}
