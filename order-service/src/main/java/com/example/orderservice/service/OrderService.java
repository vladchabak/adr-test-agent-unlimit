package com.example.orderservice.service;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;

    public OrderService(OrderRepository orderRepository, PaymentClient paymentClient) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
    }

    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order(
                request.customerName(),
                request.productName(),
                request.quantity(),
                request.totalPrice()
        );

        // Save the order first
        Order savedOrder = orderRepository.save(order);

        // Initiate payment
        var paymentResponse = paymentClient.initiatePayment(savedOrder.getId(), savedOrder.getTotalPrice());

        // Update order status based on payment response
        if (paymentResponse != null && "SUCCESS".equals(paymentResponse.status())) {
            savedOrder.setStatus("PAYMENT_INITIATED");
        } else {
            savedOrder.setStatus("PAYMENT_FAILED");
        }

        return orderRepository.save(savedOrder);
    }

    public Optional<Order> getOrder(Long id) {
        return orderRepository.findById(id);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

}
