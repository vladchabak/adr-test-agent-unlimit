package com.example.orderservice.client;

import com.example.orderservice.dto.PaymentRequest;
import com.example.orderservice.dto.PaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Service
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    private final RestTemplate restTemplate;
    private final String paymentServiceUrl;

    public PaymentClient(RestTemplate restTemplate, @Value("${payment.service.url}") String paymentServiceUrl) {
        this.restTemplate = restTemplate;
        this.paymentServiceUrl = paymentServiceUrl;
    }

    public PaymentResponse initiatePayment(Long orderId, BigDecimal amount) {
        String url = paymentServiceUrl + "/api/payments";
        try {
            return restTemplate.postForObject(url, new PaymentRequest(orderId, amount), PaymentResponse.class);
        } catch (Exception e) {
            log.error("Failed to initiate payment for order {}: ", orderId, e);
            return null;
        }
    }
}
