package com.ecommerce.order.infrastructure.web;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import com.ecommerce.order.infrastructure.persistence.OrderRow;
import com.ecommerce.order.infrastructure.persistence.OrderRepository;

/**
 * Phase 1 walking skeleton endpoint. It talks straight to the repository to validate the
 * WebFlux + R2DBC + Flyway axis end-to-end. Real use cases, domain rules, RFC 7807 errors and
 * the full state machine replace this controller from Phase 3/6.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderRepository repository;

    public OrderController(OrderRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public Mono<ResponseEntity<OrderResponse>> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderRow row = new OrderRow();
        row.setId(UUID.randomUUID());
        row.setCustomerId(request.customerId());
        row.setStatus("CREATED");
        Instant now = Instant.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        // version stays null -> Spring Data R2DBC performs an INSERT.
        return repository.save(row)
                .map(OrderController::toResponse)
                .map(response -> ResponseEntity
                        .created(URI.create("/api/v1/orders/" + response.id()))
                        .body(response));
    }

    @GetMapping("/{orderId}")
    public Mono<ResponseEntity<OrderResponse>> getById(@PathVariable UUID orderId) {
        return repository.findById(orderId)
                .map(OrderController::toResponse)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private static OrderResponse toResponse(OrderRow row) {
        return new OrderResponse(row.getId(), row.getCustomerId(), row.getStatus());
    }

    public record CreateOrderRequest(@NotBlank String customerId) {
    }

    public record OrderResponse(UUID id, String customerId, String status) {
    }
}
