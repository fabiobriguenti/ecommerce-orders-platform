package com.ecommerce.order.infrastructure.external;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import com.ecommerce.order.domain.vo.CustomerId;

class CustomerHttpClientIT extends AbstractWireMockIT {

    private CustomerHttpClient client() {
        return new CustomerHttpClient(webClient());
    }

    @Test
    void activeCustomerMapsToActiveView() {
        StepVerifier.create(client().findById(CustomerId.of("cust-active")))
                .assertNext(view -> Assertions.assertThat(view.active()).isTrue())
                .verifyComplete();
    }

    @Test
    void blockedCustomerMapsToInactiveView() {
        StepVerifier.create(client().findById(CustomerId.of("cust-blocked")))
                .assertNext(view -> Assertions.assertThat(view.active()).isFalse())
                .verifyComplete();
    }

    @Test
    void unknownCustomerIsEmpty() {
        StepVerifier.create(client().findById(CustomerId.of("cust-does-not-exist")))
                .verifyComplete();
    }
}
