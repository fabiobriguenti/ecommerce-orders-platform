package com.ecommerce.order.domain.port;

import java.util.UUID;

import reactor.core.publisher.Mono;

import com.ecommerce.order.domain.vo.Money;

/**
 * Outbound port to the external Payment Gateway (simulated by WireMock from Phase 5). The adapter
 * applies the circuit breaker + timeout (ADR-14) so gateway instability cannot take down the service.
 */
public interface PaymentGatewayPort {

    Mono<ChargeResult> charge(ChargeCommand command);

    record ChargeCommand(UUID orderId, UUID paymentId, Money amount) {
    }

    record ChargeResult(boolean approved, String gatewayReference) {
    }
}
