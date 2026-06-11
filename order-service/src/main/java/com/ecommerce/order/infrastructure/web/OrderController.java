package com.ecommerce.order.infrastructure.web;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.ecommerce.order.application.order.AddItem;
import com.ecommerce.order.application.order.CancelOrder;
import com.ecommerce.order.application.order.ConfirmOrder;
import com.ecommerce.order.application.order.CreateOrder;
import com.ecommerce.order.application.order.GetOrder;
import com.ecommerce.order.application.order.ListOrdersByCustomer;
import com.ecommerce.order.application.order.RemoveItem;
import com.ecommerce.order.infrastructure.web.dto.AddItemRequest;
import com.ecommerce.order.infrastructure.web.dto.CreateOrderRequest;
import com.ecommerce.order.infrastructure.web.dto.OrderResponse;

/**
 * Orders REST API (ADR-08). Each mutating handler runs inside a reactive transaction so the
 * aggregate change and its outbox event (ADR-04) commit atomically. Business-rule violations
 * surface as domain/application exceptions and are translated to RFC 7807 responses by
 * {@link ProblemDetailExceptionHandler}; the {@code Idempotency-Key} header is honoured by
 * {@link IdempotencyKeyFilter}.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrder createOrder;
    private final GetOrder getOrder;
    private final ListOrdersByCustomer listOrdersByCustomer;
    private final AddItem addItem;
    private final RemoveItem removeItem;
    private final ConfirmOrder confirmOrder;
    private final CancelOrder cancelOrder;
    private final TransactionalOperator tx;

    public OrderController(CreateOrder createOrder, GetOrder getOrder,
                           ListOrdersByCustomer listOrdersByCustomer, AddItem addItem,
                           RemoveItem removeItem, ConfirmOrder confirmOrder, CancelOrder cancelOrder,
                           TransactionalOperator tx) {
        this.createOrder = createOrder;
        this.getOrder = getOrder;
        this.listOrdersByCustomer = listOrdersByCustomer;
        this.addItem = addItem;
        this.removeItem = removeItem;
        this.confirmOrder = confirmOrder;
        this.cancelOrder = cancelOrder;
        this.tx = tx;
    }

    @PostMapping
    public Mono<ResponseEntity<OrderResponse>> create(@Valid @RequestBody CreateOrderRequest request) {
        return createOrder.handle(new CreateOrder.Command(request.customerId()))
                .as(tx::transactional)
                .map(OrderResponse::from)
                .map(body -> ResponseEntity.created(URI.create("/api/v1/orders/" + body.id())).body(body));
    }

    @GetMapping("/{orderId}")
    public Mono<OrderResponse> getById(@PathVariable UUID orderId) {
        return getOrder.handle(orderId).map(OrderResponse::from);
    }

    @GetMapping
    public Flux<OrderResponse> listByCustomer(@RequestParam String customerId) {
        return listOrdersByCustomer.handle(customerId).map(OrderResponse::from);
    }

    @PostMapping("/{orderId}/items")
    public Mono<OrderResponse> addItem(@PathVariable UUID orderId, @Valid @RequestBody AddItemRequest request) {
        return addItem.handle(new AddItem.Command(orderId, request.productId(), request.quantity()))
                .as(tx::transactional)
                .map(OrderResponse::from);
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    public Mono<OrderResponse> removeItem(@PathVariable UUID orderId, @PathVariable String itemId) {
        return removeItem.handle(orderId, itemId)
                .as(tx::transactional)
                .map(OrderResponse::from);
    }

    @PostMapping("/{orderId}/confirm")
    public Mono<OrderResponse> confirm(@PathVariable UUID orderId) {
        return confirmOrder.handle(orderId)
                .as(tx::transactional)
                .map(OrderResponse::from);
    }

    @DeleteMapping("/{orderId}")
    public Mono<ResponseEntity<OrderResponse>> cancel(@PathVariable UUID orderId) {
        return cancelOrder.handle(orderId)
                .as(tx::transactional)
                .map(OrderResponse::from)
                .map(body -> ResponseEntity.status(HttpStatus.OK).body(body));
    }
}
