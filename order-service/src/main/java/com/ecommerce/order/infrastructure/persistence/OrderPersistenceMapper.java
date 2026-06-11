package com.ecommerce.order.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ecommerce.order.domain.order.Order;
import com.ecommerce.order.domain.order.OrderItem;
import com.ecommerce.order.domain.order.OrderStatus;
import com.ecommerce.order.domain.vo.CustomerId;
import com.ecommerce.order.domain.vo.Money;
import com.ecommerce.order.domain.vo.ProductId;
import com.ecommerce.order.domain.vo.Quantity;

final class OrderPersistenceMapper {

    private OrderPersistenceMapper() {
    }

    static OrderRow toRow(Order order) {
        OrderRow row = new OrderRow();
        row.setId(order.id());
        row.setCustomerId(order.customerId().value());
        row.setStatus(order.status().name());
        order.total().ifPresentOrElse(
                total -> {
                    row.setTotalAmount(total.amount());
                    row.setTotalCurrency(total.currency());
                },
                () -> {
                    row.setTotalAmount(null);
                    row.setTotalCurrency(null);
                });
        row.setVersion(order.version());
        Instant now = Instant.now();
        row.setCreatedAt(now); // @InsertOnlyProperty: ignored on update
        row.setUpdatedAt(now);
        return row;
    }

    static List<OrderItemRow> toItemRows(Order order) {
        return order.items().stream().map(item -> {
            OrderItemRow row = new OrderItemRow();
            row.setId(UUID.randomUUID());
            row.setOrderId(order.id());
            row.setProductId(item.productId().value());
            row.setQuantity(item.quantity().value());
            if (item.unitPrice() != null) {
                row.setUnitPriceAmount(item.unitPrice().amount());
                row.setUnitPriceCurrency(item.unitPrice().currency());
            }
            return row;
        }).toList();
    }

    static Order toDomain(OrderRow row, List<OrderItemRow> itemRows) {
        List<OrderItem> items = itemRows.stream().map(r -> {
            Money price = r.getUnitPriceAmount() != null
                    ? Money.of(r.getUnitPriceAmount(), r.getUnitPriceCurrency())
                    : null;
            return new OrderItem(ProductId.of(r.getProductId()), Quantity.of(r.getQuantity()), price);
        }).toList();
        Money total = row.getTotalAmount() != null
                ? Money.of(row.getTotalAmount(), row.getTotalCurrency())
                : null;
        return Order.reconstitute(row.getId(), CustomerId.of(row.getCustomerId()),
                OrderStatus.valueOf(row.getStatus()), items, total, row.getVersion());
    }
}
