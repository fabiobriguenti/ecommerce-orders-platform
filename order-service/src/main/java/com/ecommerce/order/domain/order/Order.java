package com.ecommerce.order.domain.order;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ecommerce.order.domain.exception.EmptyOrderException;
import com.ecommerce.order.domain.exception.InvalidOrderStateException;
import com.ecommerce.order.domain.exception.ItemNotFoundException;
import com.ecommerce.order.domain.exception.MissingProductPriceException;
import com.ecommerce.order.domain.exception.OrderNotModifiableException;
import com.ecommerce.order.domain.vo.CustomerId;
import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.domain.vo.ProductId;
import com.ecommerce.order.domain.vo.Quantity;

/**
 * Order aggregate root. Encapsulates the lifecycle and invariants (ADR-10). Cross-aggregate rules
 * (customer must be active, max one active order per customer, current catalog prices) are enforced
 * by the use cases, which feed the required data into these methods.
 */
public class Order {

    private final UUID id;
    private final CustomerId customerId;
    private OrderStatus status;
    private final List<OrderItem> items;
    private Money total;
    private Long version;

    private Order(UUID id, CustomerId customerId, OrderStatus status, List<OrderItem> items,
                  Money total, Long version) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.items = items;
        this.total = total;
        this.version = version;
    }

    /** Creates a brand-new empty order in {@link OrderStatus#CREATED}. */
    public static Order create(UUID id, CustomerId customerId) {
        return new Order(id, customerId, OrderStatus.CREATED, new ArrayList<>(), null, null);
    }

    /** Rebuilds an order from persistence without re-running creation rules. */
    public static Order reconstitute(UUID id, CustomerId customerId, OrderStatus status,
                                     List<OrderItem> items, Money total, Long version) {
        return new Order(id, customerId, status, new ArrayList<>(items), total, version);
    }

    /**
     * Adds an item. If the product is already in the order, its quantity is incremented
     * rather than duplicated. Only allowed while the order is still open (CREATED).
     */
    public void addItem(ProductId productId, Quantity quantity) {
        requireModifiable();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).productId().equals(productId)) {
                items.set(i, items.get(i).incrementBy(quantity));
                return;
            }
        }
        items.add(OrderItem.of(productId, quantity));
    }

    /** Removes an item by product. Fails if the product is not part of the order. */
    public void removeItem(ProductId productId) {
        requireModifiable();
        boolean removed = items.removeIf(item -> item.productId().equals(productId));
        if (!removed) {
            throw new ItemNotFoundException(productId);
        }
    }

    /**
     * Confirms the order, freezing the total from the current prices supplied by the caller
     * (captured at confirmation time, not at add time). Idempotent: confirming an already
     * confirmed order is a no-op.
     *
     * @return {@code true} if this call performed the confirmation, {@code false} if it was
     *         already confirmed (no-op).
     */
    public boolean confirm(Map<ProductId, Money> currentPrices) {
        if (status == OrderStatus.CONFIRMED) {
            return false;
        }
        if (status != OrderStatus.CREATED) {
            throw new InvalidOrderStateException("confirm", status);
        }
        if (items.isEmpty()) {
            throw new EmptyOrderException(id);
        }
        List<OrderItem> priced = new ArrayList<>(items.size());
        Money sum = null;
        for (OrderItem item : items) {
            Money price = currentPrices.get(item.productId());
            if (price == null) {
                throw new MissingProductPriceException(item.productId());
            }
            OrderItem pricedItem = item.pricedAt(price);
            priced.add(pricedItem);
            sum = (sum == null) ? pricedItem.subtotal() : sum.add(pricedItem.subtotal());
        }
        items.clear();
        items.addAll(priced);
        total = sum;
        status = OrderStatus.CONFIRMED;
        return true;
    }

    /** Moves the order into payment processing (initial attempt or retry after a rejection). */
    public void awaitPayment() {
        transitionTo(OrderStatus.AWAITING_PAYMENT, "startPayment");
    }

    /** Marks the order as paid after the gateway approves the payment. */
    public void markPaid() {
        transitionTo(OrderStatus.PAID, "markPaid");
    }

    /** Marks the order as rejected so a new payment attempt is allowed. */
    public void markPaymentRejected() {
        transitionTo(OrderStatus.PAYMENT_REJECTED, "markPaymentRejected");
    }

    /**
     * Cancels the order. Allowed only while payment has not been approved. Idempotent:
     * cancelling an already cancelled order is a no-op.
     */
    public void cancel() {
        if (status == OrderStatus.CANCELLED) {
            return;
        }
        if (!status.canTransitionTo(OrderStatus.CANCELLED)) {
            throw new InvalidOrderStateException("cancel", status);
        }
        status = OrderStatus.CANCELLED;
    }

    private void transitionTo(OrderStatus target, String operation) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidOrderStateException(operation, status);
        }
        status = target;
    }

    private void requireModifiable() {
        if (!status.acceptsItems()) {
            throw new OrderNotModifiableException(id, status);
        }
    }

    public UUID id() {
        return id;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public OrderStatus status() {
        return status;
    }

    public List<OrderItem> items() {
        return List.copyOf(items);
    }

    public Optional<Money> total() {
        return Optional.ofNullable(total);
    }

    /** Optimistic-locking version (ADR-05). {@code null} for a not-yet-persisted order. */
    public Long version() {
        return version;
    }

    /** Set by the persistence adapter after a successful save so subsequent saves use the new version. */
    public void assignVersion(Long version) {
        this.version = version;
    }
}
