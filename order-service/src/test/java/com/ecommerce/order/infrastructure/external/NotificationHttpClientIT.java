package com.ecommerce.order.infrastructure.external;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import com.ecommerce.order.domain.port.NotificationPort.NotificationMessage;

class NotificationHttpClientIT extends AbstractWireMockIT {

    @Test
    void sendsNotificationAndCompletes() {
        NotificationHttpClient client = new NotificationHttpClient(webClient());
        StepVerifier.create(client.send(new NotificationMessage(UUID.randomUUID(), "OrderConfirmed")))
                .verifyComplete();
    }
}
