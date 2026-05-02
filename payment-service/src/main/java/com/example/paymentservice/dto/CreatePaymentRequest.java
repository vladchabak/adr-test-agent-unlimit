package com.example.paymentservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreatePaymentRequest(
    @NotNull Long orderId,
    @NotNull @Positive BigDecimal amount
) {}
