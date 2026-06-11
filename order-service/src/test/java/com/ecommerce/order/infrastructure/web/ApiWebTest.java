package com.ecommerce.order.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.ecommerce.order.application.exception.OrderNotFoundException;
import com.ecommerce.order.application.order.AddItem;
import com.ecommerce.order.application.order.CancelOrder;
import com.ecommerce.order.application.order.ConfirmOrder;
import com.ecommerce.order.application.order.CreateOrder;
import com.ecommerce.order.application.order.GetOrder;
import com.ecommerce.order.application.order.ListOrdersByCustomer;
import com.ecommerce.order.application.order.RemoveItem;
import com.ecommerce.order.application.payment.GetPayment;
import com.ecommerce.order.application.payment.HandlePaymentCallback;
import com.ecommerce.order.application.payment.StartPayment;
import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.payment.Payment;
import com.ecommerce.order.domain.vo.CustomerId;
import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.infrastructure.config.SecurityConfig;
import com.ecommerce.order.infrastructure.web.security.SecurityProblemSupport;

/**
 * Web-layer slice test: controllers + real security chain + RFC 7807 advice, with the use cases
 * mocked (no DB/Docker). Verifies routing, scope-based authorization and Problem Detail mapping.
 * The full stack through Testcontainers + WireMock is covered by the {@code *IT} suite.
 */
@WebFluxTest(controllers = {OrderController.class, PaymentController.class})
@Import({SecurityConfig.class, SecurityProblemSupport.class, ProblemDetailExceptionHandler.class})
class ApiWebTest {

    @Autowired
    WebTestClient client;

    @MockitoBean CreateOrder createOrder;
    @MockitoBean GetOrder getOrder;
    @MockitoBean ListOrdersByCustomer listOrdersByCustomer;
    @MockitoBean AddItem addItem;
    @MockitoBean RemoveItem removeItem;
    @MockitoBean ConfirmOrder confirmOrder;
    @MockitoBean CancelOrder cancelOrder;
    @MockitoBean StartPayment startPayment;
    @MockitoBean GetPayment getPayment;
    @MockitoBean HandlePaymentCallback handlePaymentCallback;
    @MockitoBean TransactionalOperator tx;
    @MockitoBean ReactiveJwtDecoder jwtDecoder; // required by the resource-server config; bypassed by mockJwt()
    // The web filters are part of the slice; the idempotency store is only touched when an
    // Idempotency-Key header is present (none here), so a bare mock suffices.
    @MockitoBean com.ecommerce.order.infrastructure.idempotency.IdempotencyStore idempotencyStore;

    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PAYMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void txPassthrough() {
        given(tx.transactional(any(Mono.class))).willAnswer(inv -> inv.getArgument(0));
    }

    private static Order sampleOrder() {
        return Order.create(ORDER_ID, CustomerId.of("cust-active"));
    }

    private static Payment samplePayment() {
        return Payment.initiate(PAYMENT_ID, ORDER_ID, Money.of("20.00", "BRL"));
    }

    private static SimpleGrantedAuthority scope(String s) {
        return new SimpleGrantedAuthority("SCOPE_" + s);
    }

    // ---- Authentication / authorization --------------------------------------------------------

    @Test
    void rejectsAnonymousMutation() {
        client.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"customerId\":\"cust-active\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectsInsufficientScope() {
        client.mutateWith(mockJwt().authorities(scope("orders:read")))
                .post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"customerId\":\"cust-active\"}")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    // ---- Orders --------------------------------------------------------------------------------

    @Test
    void createsOrder() {
        given(createOrder.handle(any())).willReturn(Mono.just(sampleOrder()));

        client.mutateWith(mockJwt().authorities(scope("orders:write")))
                .post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"customerId\":\"cust-active\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "/api/v1/orders/" + ORDER_ID)
                .expectBody()
                .jsonPath("$.id").isEqualTo(ORDER_ID.toString())
                .jsonPath("$.status").isEqualTo("CREATED");
    }

    @Test
    void rejectsInvalidCreateBody() {
        client.mutateWith(mockJwt().authorities(scope("orders:write")))
                .post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"customerId\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.title").isEqualTo("Validation failed");
    }

    @Test
    void getsOrderById() {
        given(getOrder.handle(ORDER_ID)).willReturn(Mono.just(sampleOrder()));

        client.mutateWith(mockJwt().authorities(scope("orders:read")))
                .get().uri("/api/v1/orders/{id}", ORDER_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.customerId").isEqualTo("cust-active");
    }

    @Test
    void mapsNotFoundToProblemDetail() {
        given(getOrder.handle(ORDER_ID)).willReturn(Mono.error(new OrderNotFoundException(ORDER_ID)));

        client.mutateWith(mockJwt().authorities(scope("orders:read")))
                .get().uri("/api/v1/orders/{id}", ORDER_ID)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.title").isEqualTo("Resource not found");
    }

    @Test
    void listsOrdersByCustomer() {
        given(listOrdersByCustomer.handle("cust-active")).willReturn(Flux.just(sampleOrder()));

        client.mutateWith(mockJwt().authorities(scope("orders:read")))
                .get().uri("/api/v1/orders?customerId=cust-active")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[0].id").isEqualTo(ORDER_ID.toString());
    }

    @Test
    void addsItem() {
        given(addItem.handle(any())).willReturn(Mono.just(sampleOrder()));

        client.mutateWith(mockJwt().authorities(scope("orders:write")))
                .post().uri("/api/v1/orders/{id}/items", ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"productId\":\"prod-available\",\"quantity\":2}")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void confirmsOrder() {
        given(confirmOrder.handle(ORDER_ID)).willReturn(Mono.just(sampleOrder()));

        client.mutateWith(mockJwt().authorities(scope("orders:write")))
                .post().uri("/api/v1/orders/{id}/confirm", ORDER_ID)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void cancelsOrder() {
        given(cancelOrder.handle(ORDER_ID)).willReturn(Mono.just(sampleOrder()));

        client.mutateWith(mockJwt().authorities(scope("orders:write")))
                .delete().uri("/api/v1/orders/{id}", ORDER_ID)
                .exchange()
                .expectStatus().isOk();
    }

    // ---- Payments ------------------------------------------------------------------------------

    @Test
    void startsPayment() {
        given(startPayment.handle(ORDER_ID)).willReturn(Mono.just(samplePayment()));

        client.mutateWith(mockJwt().authorities(scope("payments:write")))
                .post().uri("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"orderId\":\"" + ORDER_ID + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.orderId").isEqualTo(ORDER_ID.toString())
                .jsonPath("$.status").isEqualTo("PROCESSING");
    }

    @Test
    void paymentRequiresPaymentScope() {
        client.mutateWith(mockJwt().authorities(scope("orders:write")))
                .post().uri("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"orderId\":\"" + ORDER_ID + "\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void handlesPaymentCallback() {
        given(handlePaymentCallback.handle(PAYMENT_ID, true)).willReturn(Mono.just(samplePayment()));

        client.mutateWith(mockJwt().authorities(scope("payments:write")))
                .post().uri("/api/v1/payments/{id}/callback", PAYMENT_ID)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"status\":\"APPROVED\"}")
                .exchange()
                .expectStatus().isOk();
    }
}
