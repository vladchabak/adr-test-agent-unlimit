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

    public OrderResponse createOrder(CreateOrderRequest request) {
        Order saved = saveNewOrder(request);

        var resp = paymentClient.initiatePayment(saved.getId(), saved.getTotalPrice());
        OrderStatus status = (resp != null && "SUCCESS".equals(resp.status()))
                ? OrderStatus.PAYMENT_INITIATED : OrderStatus.PAYMENT_FAILED;

        return applyStatus(saved.getId(), status);
    }

    @Transactional
    private Order saveNewOrder(CreateOrderRequest req) {
        return orderRepository.save(new Order(req.customerName(), req.productName(),
                req.quantity(), req.totalPrice()));
    }

    @Transactional
    private OrderResponse applyStatus(Long id, OrderStatus status) {
        Order o = orderRepository.findById(id).orElseThrow();
        o.setStatus(status);
        return toResponse(o);
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
