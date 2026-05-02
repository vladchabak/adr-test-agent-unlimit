package com.example.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank String customerName,
        @NotBlank String productName,
        @NotNull @Positive Integer quantity,
        @NotNull @Positive BigDecimal totalPrice
) {}
