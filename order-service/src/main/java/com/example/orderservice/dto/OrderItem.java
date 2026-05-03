package com.example.orderservice.dto;

import java.math.BigDecimal;

public record OrderItem(
        String productName,
        Integer quantity,
        BigDecimal price
) {}
