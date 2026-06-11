package com.ecommerce.order.infrastructure.external;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import com.ecommerce.order.domain.vo.ProductId;

class CatalogHttpClientIT extends AbstractWireMockIT {

    private CatalogHttpClient client() {
        return new CatalogHttpClient(webClient());
    }

    @Test
    void availableProductMapsWithPrice() {
        StepVerifier.create(client().findById(ProductId.of("prod-available")))
                .assertNext(view -> {
                    Assertions.assertThat(view.available()).isTrue();
                    Assertions.assertThat(view.price().amount()).isEqualByComparingTo("10.00");
                })
                .verifyComplete();
    }

    @Test
    void unavailableProductMapsAsNotAvailable() {
        StepVerifier.create(client().findById(ProductId.of("prod-unavailable")))
                .assertNext(view -> Assertions.assertThat(view.available()).isFalse())
                .verifyComplete();
    }

    @Test
    void unknownProductIsEmpty() {
        StepVerifier.create(client().findById(ProductId.of("prod-does-not-exist")))
                .verifyComplete();
    }
}
