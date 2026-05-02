package com.example.orderservice.dto;

import java.math.BigDecimal;

public record CreateOrderRequest(
        String customerName,
        String productName,
        Integer quantity,
        BigDecimal totalPrice
) {
}
