package com.example.orderservice.service;

import com.example.orderservice.client.PaymentClient;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.dto.PaymentResponse;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentClient paymentClient;

    @InjectMocks
    private OrderService orderService;

    private Order savedOrder;
    private CreateOrderRequest createRequest;

    @BeforeEach
    void setUp() {
        createRequest = new CreateOrderRequest("John Doe", "Laptop", 1, new BigDecimal("999.99"));
        savedOrder = new Order("John Doe", "Laptop", 1, new BigDecimal("999.99"));
        savedOrder.setId(1L);
    }

    @Test
    void createOrder_paymentSuccess_setsPaymentInitiatedStatus() {
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(paymentClient.initiatePayment(1L, new BigDecimal("999.99")))
                .thenReturn(new PaymentResponse(1L, 1L, "SUCCESS"));

        OrderResponse result = orderService.createOrder(createRequest);

        assertThat(result.status()).isEqualTo(OrderStatus.PAYMENT_INITIATED.name());
        assertThat(result.customerName()).isEqualTo("John Doe");
        assertThat(result.totalPrice()).isEqualByComparingTo("999.99");
        verify(orderRepository, times(2)).save(any(Order.class));
    }

    @Test
    void createOrder_paymentReturnsNonSuccessStatus_setsPaymentFailedStatus() {
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(paymentClient.initiatePayment(any(), any()))
                .thenReturn(new PaymentResponse(1L, 1L, "FAILED"));

        OrderResponse result = orderService.createOrder(createRequest);

        assertThat(result.status()).isEqualTo(OrderStatus.PAYMENT_FAILED.name());
    }

    @Test
    void createOrder_paymentClientReturnsNull_setsPaymentFailedStatus() {
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(paymentClient.initiatePayment(any(), any())).thenReturn(null);

        OrderResponse result = orderService.createOrder(createRequest);

        assertThat(result.status()).isEqualTo(OrderStatus.PAYMENT_FAILED.name());
    }

    @Test
    void getOrder_existingId_returnsOrderResponse() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(savedOrder));

        Optional<OrderResponse> result = orderService.getOrder(1L);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(1L);
        assertThat(result.get().customerName()).isEqualTo("John Doe");
    }

    @Test
    void getOrder_nonExistentId_returnsEmpty() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<OrderResponse> result = orderService.getOrder(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void getAllOrders_returnsPageOfResponses() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> orderPage = new PageImpl<>(List.of(savedOrder), pageable, 1);
        when(orderRepository.findAll(pageable)).thenReturn(orderPage);

        Page<OrderResponse> result = orderService.getAllOrders(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).customerName()).isEqualTo("John Doe");
    }

    @Test
    void getAllOrders_emptyRepository_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        Page<OrderResponse> result = orderService.getAllOrders(pageable);

        assertThat(result.getContent()).isEmpty();
    }
}
