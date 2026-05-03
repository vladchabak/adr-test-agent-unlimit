package com.example.paymentservice.controller;

import com.example.paymentservice.dto.CreatePaymentRequest;
import com.example.paymentservice.dto.PaymentResponse;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import com.example.paymentservice.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Autowired
    private ObjectMapper objectMapper;

    private Payment buildPayment(Long id, Long orderId, PaymentStatus status) {
        Payment p = new Payment(orderId, new BigDecimal("99.99"));
        p.setId(id);
        p.setStatus(status);
        return p;
    }

    private PaymentResponse buildPaymentResponse(Long id, Long orderId, PaymentStatus status) {
        return new PaymentResponse(id, orderId, new BigDecimal("99.99"), status.name(), "CARD", LocalDateTime.now());
    }

    @Test
    void createPayment_validRequest_returns201WithBody() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(1L, new BigDecimal("99.99"));
        when(paymentService.createPayment(any())).thenReturn(buildPayment(1L, 1L, PaymentStatus.SUCCESS));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void createPayment_nullOrderId_returns400() throws Exception {
        String badRequest = """
                {"orderId": null, "amount": 99.99}
                """;

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPayment_negativeAmount_returns400() throws Exception {
        String badRequest = """
                {"orderId": 1, "amount": -10.00}
                """;

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPayment_zeroAmount_returns400() throws Exception {
        String badRequest = """
                {"orderId": 1, "amount": 0}
                """;

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPayment_existingId_returns200WithBody() throws Exception {
        when(paymentService.getPaymentResponse(1L))
                .thenReturn(Optional.of(buildPaymentResponse(1L, 2L, PaymentStatus.SUCCESS)));

        mockMvc.perform(get("/api/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.orderId").value(2))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentMethod").value("CARD"));
    }

    @Test
    void getPayment_nonExistentId_returns404() throws Exception {
        when(paymentService.getPaymentResponse(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/payments/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPaymentByOrderId_existingOrderId_returns200WithBody() throws Exception {
        when(paymentService.getPaymentResponseByOrderId(5L))
                .thenReturn(Optional.of(buildPaymentResponse(1L, 5L, PaymentStatus.SUCCESS)));

        mockMvc.perform(get("/api/payments/order/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(5))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void getPaymentByOrderId_nonExistentOrderId_returns404() throws Exception {
        when(paymentService.getPaymentResponseByOrderId(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/payments/order/999"))
                .andExpect(status().isNotFound());
    }
}
