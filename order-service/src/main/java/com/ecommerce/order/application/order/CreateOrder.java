package com.ecommerce.order.application.order;

import java.util.UUID;

import reactor.core.publisher.Mono;

import com.ecommerce.order.application.exception.CustomerBlockedException;
import com.ecommerce.order.application.exception.CustomerNotFoundException;
import com.ecommerce.order.application.exception.DuplicateActiveOrderException;
import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.port.CustomerPort;
import com.ecommerce.order.domain.port.OrderRepositoryPort;
import com.ecommerce.order.domain.vo.CustomerId;

/**
 * Creates an order for an existing, active customer, enforcing the "at most one active order per
 * customer" rule. Customer existence/status is validated against the Customer service (port).
 */
public class CreateOrder {

    private final CustomerPort customerPort;
    private final OrderRepositoryPort orderRepository;

    public CreateOrder(CustomerPort customerPort, OrderRepositoryPort orderRepository) {
        this.customerPort = customerPort;
        this.orderRepository = orderRepository;
    }

    public Mono<Order> handle(Command command) {
        return Mono.defer(() -> {
            CustomerId customerId = CustomerId.of(command.customerId());
            return customerPort.findById(customerId)
                    .switchIfEmpty(Mono.error(new CustomerNotFoundException(customerId)))
                    .flatMap(customer -> customer.active()
                            ? Mono.just(customer)
                            : Mono.error(new CustomerBlockedException(customerId)))
                    .flatMap(customer -> orderRepository.existsActiveByCustomerId(customerId))
                    .flatMap(active -> active
                            ? Mono.error(new DuplicateActiveOrderException(customerId))
                            : orderRepository.save(Order.create(UUID.randomUUID(), customerId)));
        });
    }

    public record Command(String customerId) {
    }
}
