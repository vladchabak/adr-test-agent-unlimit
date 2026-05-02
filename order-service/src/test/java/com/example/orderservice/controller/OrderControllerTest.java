package com.example.orderservice.controller;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    private OrderResponse sampleResponse() {
        return new OrderResponse(1L, "John Doe", "Laptop", 1, new BigDecimal("999.99"), "PAYMENT_INITIATED", null);
    }

    @Test
    void createOrder_validRequest_returns201WithBody() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("John Doe", "Laptop", 1, new BigDecimal("999.99"));
        when(orderService.createOrder(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.customerName").value("John Doe"))
                .andExpect(jsonPath("$.status").value("PAYMENT_INITIATED"));
    }

    @Test
    void createOrder_blankCustomerName_returns400() throws Exception {
        String badRequest = """
                {"customerName": "", "productName": "Laptop", "quantity": 1, "totalPrice": 99.99}
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_nullQuantity_returns400() throws Exception {
        String badRequest = """
                {"customerName": "John", "productName": "Laptop", "quantity": null, "totalPrice": 99.99}
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_negativeTotalPrice_returns400() throws Exception {
        String badRequest = """
                {"customerName": "John", "productName": "Laptop", "quantity": 1, "totalPrice": -1}
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrder_existingId_returns200WithBody() throws Exception {
        when(orderService.getOrder(1L)).thenReturn(Optional.of(sampleResponse()));

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.customerName").value("John Doe"));
    }

    @Test
    void getOrder_nonExistentId_returns404() throws Exception {
        when(orderService.getOrder(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllOrders_returnsPagedResults() throws Exception {
        Page<OrderResponse> page = new PageImpl<>(List.of(sampleResponse()));
        when(orderService.getAllOrders(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerName").value("John Doe"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAllOrders_emptyResult_returnsEmptyPage() throws Exception {
        when(orderService.getAllOrders(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }
}
