package com.ecommerce.order.infrastructure.external;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import com.ecommerce.order.domain.port.CustomerPort;
import com.ecommerce.order.domain.vo.CustomerId;

/**
 * Customer service adapter. HTTP contract (WireMock): 200 active, 422 blocked, 404 not found.
 */
@Component
public class CustomerHttpClient implements CustomerPort {

    private final WebClient webClient;

    public CustomerHttpClient(WebClient externalWebClient) {
        this.webClient = externalWebClient;
    }

    @Override
    public Mono<CustomerView> findById(CustomerId id) {
        return webClient.get()
                .uri("/customers/{id}", id.value())
                .exchangeToMono(response -> switch (response.statusCode().value()) {
                    case 200 -> response.bodyToMono(CustomerResponse.class)
                            .map(body -> new CustomerView(id, "ACTIVE".equalsIgnoreCase(body.status())));
                    case 422 -> response.releaseBody().thenReturn(new CustomerView(id, false));
                    case 404 -> response.releaseBody().then(Mono.empty());
                    default -> response.createException().flatMap(Mono::error);
                });
    }

    record CustomerResponse(String id, String status) {
    }
}
