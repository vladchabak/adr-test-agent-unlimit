package com.example.paymentservice.service;

import com.example.paymentservice.dto.CreatePaymentRequest;
import com.example.paymentservice.dto.PaymentResponse;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment createPayment(CreatePaymentRequest request) {
        Payment payment = new Payment(request.orderId(), request.amount());

        boolean success = processPayment(payment);
        payment.setStatus(success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);

        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Optional<Payment> getPayment(Long id) {
        return paymentRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentResponse> getPaymentResponse(Long id) {
        return paymentRepository.findById(id).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<Payment> getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentResponse> getPaymentResponseByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId).map(this::toResponse);
    }

    private boolean processPayment(Payment payment) {
        // Placeholder for real payment provider integration
        return true;
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
            payment.getId(),
            payment.getOrderId(),
            payment.getAmount(),
            payment.getStatus().name(),
            payment.getPaymentMethod(),
            payment.getCreatedAt()
        );
    }
}
