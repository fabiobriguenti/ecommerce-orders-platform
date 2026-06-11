package com.ecommerce.order.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("order_items")
public class OrderItemRow {

    @Id
    private UUID id;

    @Column("order_id")
    private UUID orderId;

    @Column("product_id")
    private String productId;

    private Integer quantity;

    @Column("unit_price_amount")
    private BigDecimal unitPriceAmount;

    @Column("unit_price_currency")
    private String unitPriceCurrency;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPriceAmount() {
        return unitPriceAmount;
    }

    public void setUnitPriceAmount(BigDecimal unitPriceAmount) {
        this.unitPriceAmount = unitPriceAmount;
    }

    public String getUnitPriceCurrency() {
        return unitPriceCurrency;
    }

    public void setUnitPriceCurrency(String unitPriceCurrency) {
        this.unitPriceCurrency = unitPriceCurrency;
    }
}
