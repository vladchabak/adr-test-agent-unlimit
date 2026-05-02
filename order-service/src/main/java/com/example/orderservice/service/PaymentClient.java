package com.example.orderservice.service;

import com.example.orderservice.dto.PaymentRequest;
import com.example.orderservice.dto.PaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class PaymentClient {

    private final RestTemplate restTemplate;

    @Value("${payment.service.url}")
    private String paymentServiceUrl;

    public PaymentClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public PaymentResponse initiatePayment(Long orderId, java.math.BigDecimal amount) {
        PaymentRequest paymentRequest = new PaymentRequest(orderId, amount);
        String url = paymentServiceUrl + "/api/payments";

        try {
            PaymentResponse response = restTemplate.postForObject(
                    url,
                    paymentRequest,
                    PaymentResponse.class
            );
            return response;
        } catch (Exception e) {
            // Log error but don't fail the order creation
            System.err.println("Failed to initiate payment for order " + orderId + ": " + e.getMessage());
            return null;
        }
    }

}
