package com.ecommerce.order.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ecommerce.order.application.order.AddItem;
import com.ecommerce.order.application.order.CancelOrder;
import com.ecommerce.order.application.order.ConfirmOrder;
import com.ecommerce.order.application.order.CreateOrder;
import com.ecommerce.order.application.order.GetOrder;
import com.ecommerce.order.application.order.ListOrdersByCustomer;
import com.ecommerce.order.application.order.RemoveItem;
import com.ecommerce.order.application.payment.GetPayment;
import com.ecommerce.order.application.payment.HandlePaymentCallback;
import com.ecommerce.order.application.payment.PaymentResultProcessor;
import com.ecommerce.order.application.payment.StartPayment;
import com.ecommerce.order.domain.port.CatalogPort;
import com.ecommerce.order.domain.port.CustomerPort;
import com.ecommerce.order.domain.port.DomainEventPublisherPort;
import com.ecommerce.order.domain.port.OrderRepositoryPort;
import com.ecommerce.order.domain.port.PaymentGatewayPort;
import com.ecommerce.order.domain.port.PaymentRepositoryPort;

/**
 * Wires the framework-free use cases as Spring beans now that every port has an adapter. The
 * transactional boundary (save + outbox atomicity) is applied at the web entry points in Phase 6.
 */
@Configuration
public class UseCaseConfiguration {

    @Bean
    CreateOrder createOrder(CustomerPort customerPort, OrderRepositoryPort orderRepository) {
        return new CreateOrder(customerPort, orderRepository);
    }

    @Bean
    GetOrder getOrder(OrderRepositoryPort orderRepository) {
        return new GetOrder(orderRepository);
    }

    @Bean
    ListOrdersByCustomer listOrdersByCustomer(OrderRepositoryPort orderRepository) {
        return new ListOrdersByCustomer(orderRepository);
    }

    @Bean
    AddItem addItem(OrderRepositoryPort orderRepository, CatalogPort catalogPort) {
        return new AddItem(orderRepository, catalogPort);
    }

    @Bean
    RemoveItem removeItem(OrderRepositoryPort orderRepository) {
        return new RemoveItem(orderRepository);
    }

    @Bean
    ConfirmOrder confirmOrder(OrderRepositoryPort orderRepository, CatalogPort catalogPort,
                              DomainEventPublisherPort eventPublisher) {
        return new ConfirmOrder(orderRepository, catalogPort, eventPublisher);
    }

    @Bean
    CancelOrder cancelOrder(OrderRepositoryPort orderRepository, DomainEventPublisherPort eventPublisher) {
        return new CancelOrder(orderRepository, eventPublisher);
    }

    @Bean
    PaymentResultProcessor paymentResultProcessor(OrderRepositoryPort orderRepository,
                                                  PaymentRepositoryPort paymentRepository,
                                                  DomainEventPublisherPort eventPublisher) {
        return new PaymentResultProcessor(orderRepository, paymentRepository, eventPublisher);
    }

    @Bean
    StartPayment startPayment(OrderRepositoryPort orderRepository, PaymentRepositoryPort paymentRepository,
                              PaymentGatewayPort gateway, PaymentResultProcessor resultProcessor) {
        return new StartPayment(orderRepository, paymentRepository, gateway, resultProcessor);
    }

    @Bean
    GetPayment getPayment(PaymentRepositoryPort paymentRepository) {
        return new GetPayment(paymentRepository);
    }

    @Bean
    HandlePaymentCallback handlePaymentCallback(PaymentRepositoryPort paymentRepository,
                                                OrderRepositoryPort orderRepository,
                                                PaymentResultProcessor resultProcessor) {
        return new HandlePaymentCallback(paymentRepository, orderRepository, resultProcessor);
    }
}
