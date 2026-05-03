package com.example.orderservice.service;

import com.example.orderservice.client.PaymentClient;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;

    public OrderService(OrderRepository orderRepository, PaymentClient paymentClient) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
    }

    // Not @Transactional: save initial order first (commits immediately), then call payment
    // service outside any transaction so the DB connection is not held during the HTTP call,
    // then persist the status update in a second short transaction.
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = new Order(
                request.customerName(),
                request.productName(),
                request.quantity(),
                request.totalPrice()
        );

        Order savedOrder = orderRepository.save(order);

        var paymentResponse = paymentClient.initiatePayment(savedOrder.getId(), savedOrder.getTotalPrice());

        OrderStatus newStatus = (paymentResponse != null && "SUCCESS".equals(paymentResponse.status()))
                ? OrderStatus.PAYMENT_INITIATED
                : OrderStatus.PAYMENT_FAILED;
        savedOrder.setStatus(newStatus);
        return toResponse(orderRepository.save(savedOrder));
    }

    @Transactional(readOnly = true)
    public Optional<OrderResponse> getOrder(Long id) {
        return orderRepository.findById(id).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(this::toResponse);
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerName(),
                order.getProductName(),
                order.getQuantity(),
                order.getTotalPrice(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
