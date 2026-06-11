package com.ecommerce.order.infrastructure.external;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import com.ecommerce.order.domain.port.CatalogPort;
import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.domain.vo.ProductId;

/**
 * Catalog service adapter. HTTP contract (WireMock): 200 available with price, 422 unavailable,
 * 404 not found.
 */
@Component
public class CatalogHttpClient implements CatalogPort {

    private final WebClient webClient;

    public CatalogHttpClient(WebClient externalWebClient) {
        this.webClient = externalWebClient;
    }

    @Override
    public Mono<ProductView> findById(ProductId id) {
        return webClient.get()
                .uri("/products/{id}", id.value())
                .exchangeToMono(response -> switch (response.statusCode().value()) {
                    case 200 -> response.bodyToMono(ProductResponse.class)
                            .map(body -> new ProductView(id, body.available(),
                                    body.price() != null
                                            ? Money.of(body.price().amount(), body.price().currency())
                                            : null));
                    case 422 -> response.releaseBody().thenReturn(new ProductView(id, false, null));
                    case 404 -> response.releaseBody().then(Mono.empty());
                    default -> response.createException().flatMap(Mono::error);
                });
    }

    record ProductResponse(String id, boolean available, MoneyDto price) {
    }

    record MoneyDto(BigDecimal amount, String currency) {
    }
}
