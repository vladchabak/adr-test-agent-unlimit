package com.example.orderservice.client;

import com.example.orderservice.dto.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentClientTest {

    @Mock
    private RestTemplate restTemplate;

    private PaymentClient paymentClient;

    @BeforeEach
    void setUp() {
        paymentClient = new PaymentClient(restTemplate, "http://localhost:8081");
    }

    @Test
    void initiatePayment_success_returnsPaymentResponse() {
        PaymentResponse expected = new PaymentResponse(1L, 1L, "SUCCESS");
        when(restTemplate.postForObject(
                eq("http://localhost:8081/api/payments"),
                any(),
                eq(PaymentResponse.class)))
                .thenReturn(expected);

        PaymentResponse result = paymentClient.initiatePayment(1L, new BigDecimal("99.99"));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.orderId()).isEqualTo(1L);
    }

    @Test
    void initiatePayment_connectionRefused_returnsNull() {
        when(restTemplate.postForObject(anyString(), any(), eq(PaymentResponse.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        PaymentResponse result = paymentClient.initiatePayment(1L, new BigDecimal("99.99"));

        assertThat(result).isNull();
    }

    @Test
    void initiatePayment_unexpectedException_returnsNull() {
        when(restTemplate.postForObject(anyString(), any(), eq(PaymentResponse.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        PaymentResponse result = paymentClient.initiatePayment(2L, new BigDecimal("50.00"));

        assertThat(result).isNull();
    }

    @Test
    void initiatePayment_buildsCorrectUrl() {
        PaymentClient clientWithCustomUrl = new PaymentClient(restTemplate, "http://payment-svc:8081");
        when(restTemplate.postForObject(
                eq("http://payment-svc:8081/api/payments"),
                any(),
                eq(PaymentResponse.class)))
                .thenReturn(new PaymentResponse(1L, 1L, "SUCCESS"));

        PaymentResponse result = clientWithCustomUrl.initiatePayment(1L, BigDecimal.TEN);

        assertThat(result).isNotNull();
    }
}
