package com.ecommerce.order.infrastructure.external;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import com.ecommerce.order.domain.port.NotificationPort;

/**
 * Notification service adapter. HTTP contract (WireMock): 202 accepted.
 */
@Component
public class NotificationHttpClient implements NotificationPort {

    private final WebClient webClient;

    public NotificationHttpClient(WebClient externalWebClient) {
        this.webClient = externalWebClient;
    }

    @Override
    public Mono<Void> send(NotificationMessage message) {
        return webClient.post()
                .uri("/notifications")
                .bodyValue(new NotificationRequest(message.orderId().toString(), message.type()))
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    record NotificationRequest(String orderId, String type) {
    }
}
