package com.ecommerce.order.infrastructure.external;

import java.math.BigDecimal;
import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import com.ecommerce.order.application.exception.PaymentGatewayUnavailableException;
import com.ecommerce.order.domain.port.PaymentGatewayPort;

/**
 * Payment gateway adapter (ADR-14). Wraps the call in a TimeLimiter (inner) and CircuitBreaker
 * (outer) so timeouts count as failures and an unstable gateway (503) trips the breaker, failing
 * fast with {@link PaymentGatewayUnavailableException} instead of cascading the outage.
 *
 * <p>HTTP contract (WireMock): 200 {"status":"APPROVED"|"REJECTED"}, 503 when unstable.
 */
@Component
public class PaymentGatewayHttpClient implements PaymentGatewayPort {

    private static final String INSTANCE = "paymentGateway";

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Duration timeout;

    public PaymentGatewayHttpClient(WebClient externalWebClient,
                                    CircuitBreakerRegistry circuitBreakerRegistry,
                                    TimeLimiterRegistry timeLimiterRegistry) {
        this.webClient = externalWebClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(INSTANCE);
        // Reactor's native timeout is the reactive equivalent of Resilience4j's TimeLimiter;
        // the configured duration is reused from the timelimiter instance.
        this.timeout = timeLimiterRegistry.timeLimiter(INSTANCE).getTimeLimiterConfig().getTimeoutDuration();
    }

    @Override
    public Mono<ChargeResult> charge(ChargeCommand command) {
        return webClient.post()
                .uri("/payments")
                .bodyValue(new GatewayRequest(
                        command.orderId().toString(),
                        command.paymentId().toString(),
                        command.amount().amount(),
                        command.amount().currency()))
                .retrieve()
                .bodyToMono(GatewayResponse.class)
                .map(body -> new ChargeResult("APPROVED".equalsIgnoreCase(body.status()), body.reference()))
                .timeout(timeout)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorMap(CallNotPermittedException.class,
                        e -> new PaymentGatewayUnavailableException(command.orderId()));
    }

    record GatewayRequest(String orderId, String paymentId, BigDecimal amount, String currency) {
    }

    record GatewayResponse(String status, String reference) {
    }
}
