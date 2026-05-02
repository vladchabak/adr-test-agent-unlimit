package com.example.orderservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        String customerName,
        String productName,
        Integer quantity,
        BigDecimal totalPrice,
        String status,
        LocalDateTime createdAt
) {}
