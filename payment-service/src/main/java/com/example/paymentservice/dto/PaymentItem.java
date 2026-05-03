package com.example.paymentservice.dto;

import java.math.BigDecimal;

public record PaymentItem(
        String productName,
        Integer quantity,
        BigDecimal price
) {}
