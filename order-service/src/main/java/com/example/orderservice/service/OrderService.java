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
    private final EmailNotificationClient emailNotificationClient;

    public OrderService(OrderRepository orderRepository, PaymentClient paymentClient, EmailNotificationClient emailNotificationClient) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
        this.emailNotificationClient = emailNotificationClient;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = new Order(
                request.customerName(),
                request.productName(),
                request.quantity(),
                request.totalPrice()
        );

        Order savedOrder = orderRepository.save(order);

        var paymentResponse = paymentClient.initiatePayment(savedOrder.getId(), savedOrder.getTotalPrice());

        if (paymentResponse != null && "SUCCESS".equals(paymentResponse.status())) {
            savedOrder.setStatus(OrderStatus.PAYMENT_INITIATED);
        } else {
            savedOrder.setStatus(OrderStatus.PAYMENT_FAILED);
        }

        OrderResponse response = toResponse(orderRepository.save(savedOrder));

        // Send confirmation email via external SendGrid service
        emailNotificationClient.sendOrderConfirmationEmail(
                request.customerName(),
                request.customerName(),
                savedOrder.getId()
        );

        return response;
    }

    public Optional<OrderResponse> getOrder(Long id) {
        return orderRepository.findById(id).map(this::toResponse);
    }

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
                order.getStatus().name(),
                order.getCreatedAt()
        );
    }
}
