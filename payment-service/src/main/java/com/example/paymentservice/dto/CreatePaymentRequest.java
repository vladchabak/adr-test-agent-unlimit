package com.example.paymentservice.dto;

import java.math.BigDecimal;

public record CreatePaymentRequest(
    Long orderId,
    BigDecimal amount
) {
}
