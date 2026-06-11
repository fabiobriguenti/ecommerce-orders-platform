package com.ecommerce.order.infrastructure.external;

import java.time.Duration;
import java.util.UUID;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import com.ecommerce.order.application.exception.PaymentGatewayUnavailableException;
import com.ecommerce.order.domain.port.PaymentGatewayPort.ChargeCommand;
import com.ecommerce.order.domain.vo.Money;

class PaymentGatewayHttpClientIT extends AbstractWireMockIT {

    private PaymentGatewayHttpClient client() {
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        TimeLimiterRegistry tlRegistry = TimeLimiterRegistry.of(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .build());
        return new PaymentGatewayHttpClient(webClient(), cbRegistry, tlRegistry);
    }

    private ChargeCommand command(String amount) {
        return new ChargeCommand(UUID.randomUUID(), UUID.randomUUID(), Money.of(amount, "BRL"));
    }

    @Test
    void approvedAmountReturnsApproved() {
        StepVerifier.create(client().charge(command("10.00")))
                .assertNext(result -> Assertions.assertThat(result.approved()).isTrue())
                .verifyComplete();
    }

    @Test
    void rejectedAmountReturnsNotApproved() {
        StepVerifier.create(client().charge(command("77.77")))
                .assertNext(result -> Assertions.assertThat(result.approved()).isFalse())
                .verifyComplete();
    }

    @Test
    void unstableGatewayTripsBreakerAndFailsFast() {
        PaymentGatewayHttpClient client = client();

        // The 503 responses are recorded as failures until the breaker opens...
        StepVerifier.create(client.charge(command("99.99"))).expectError().verify();
        StepVerifier.create(client.charge(command("99.99"))).expectError().verify();

        // ...after which the call is short-circuited with a clear domain error.
        StepVerifier.create(client.charge(command("99.99")))
                .expectError(PaymentGatewayUnavailableException.class)
                .verify();
    }
}
