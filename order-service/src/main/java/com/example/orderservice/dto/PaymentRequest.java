package com.example.orderservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record PaymentRequest(
        Long orderId,
        BigDecimal amount,
        List<OrderItem> items
) {
}
