package com.ecommerce.order.infrastructure.web.dto;

import java.math.BigDecimal;

import com.ecommerce.order.domain.vo.Money;

/** RFC-friendly representation of a {@link Money} value object. */
public record MoneyResponse(BigDecimal amount, String currency) {

    public static MoneyResponse from(Money money) {
        return money == null ? null : new MoneyResponse(money.amount(), money.currency());
    }
}
