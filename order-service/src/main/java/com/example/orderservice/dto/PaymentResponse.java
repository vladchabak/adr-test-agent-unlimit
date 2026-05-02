package com.example.orderservice.dto;

public record PaymentResponse(
        Long id,
        Long orderId,
        String status
) {
}
