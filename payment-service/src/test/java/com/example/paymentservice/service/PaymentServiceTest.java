package com.example.paymentservice.service;

import com.example.paymentservice.dto.CreatePaymentRequest;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private Payment buildPayment(Long orderId, PaymentStatus status) {
        Payment p = new Payment(orderId, new BigDecimal("99.99"));
        p.setStatus(status);
        return p;
    }

    @Test
    void createPayment_success_savesPaymentWithSuccessStatus() {
        CreatePaymentRequest request = new CreatePaymentRequest(1L, new BigDecimal("99.99"));
        Payment saved = buildPayment(1L, PaymentStatus.SUCCESS);
        saved.setId(1L);
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);

        Payment result = paymentService.createPayment(request);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.getOrderId()).isEqualTo(1L);
    }

    @Test
    void createPayment_setsPaymentMethodToCard() {
        CreatePaymentRequest request = new CreatePaymentRequest(2L, new BigDecimal("50.00"));
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        Payment saved = buildPayment(2L, PaymentStatus.SUCCESS);
        when(paymentRepository.save(captor.capture())).thenReturn(saved);

        paymentService.createPayment(request);

        assertThat(captor.getValue().getPaymentMethod()).isEqualTo("CARD");
    }

    @Test
    void createPayment_persistsCorrectAmount() {
        BigDecimal amount = new BigDecimal("149.95");
        CreatePaymentRequest request = new CreatePaymentRequest(3L, amount);
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        Payment saved = buildPayment(3L, PaymentStatus.SUCCESS);
        when(paymentRepository.save(captor.capture())).thenReturn(saved);

        paymentService.createPayment(request);

        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(amount);
    }

    @Test
    void createPayment_callsRepositorySaveOnce() {
        CreatePaymentRequest request = new CreatePaymentRequest(1L, new BigDecimal("10.00"));
        when(paymentRepository.save(any())).thenReturn(buildPayment(1L, PaymentStatus.SUCCESS));

        paymentService.createPayment(request);

        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    void getPayment_existingId_returnsPayment() {
        Payment payment = buildPayment(1L, PaymentStatus.SUCCESS);
        payment.setId(1L);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        Optional<Payment> result = paymentService.getPayment(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
        assertThat(result.get().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void getPayment_nonExistentId_returnsEmpty() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Payment> result = paymentService.getPayment(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void getPaymentByOrderId_existingOrderId_returnsPayment() {
        Payment payment = buildPayment(5L, PaymentStatus.SUCCESS);
        when(paymentRepository.findByOrderId(5L)).thenReturn(Optional.of(payment));

        Optional<Payment> result = paymentService.getPaymentByOrderId(5L);

        assertThat(result).isPresent();
        assertThat(result.get().getOrderId()).isEqualTo(5L);
    }

    @Test
    void getPaymentByOrderId_nonExistentOrderId_returnsEmpty() {
        when(paymentRepository.findByOrderId(999L)).thenReturn(Optional.empty());

        Optional<Payment> result = paymentService.getPaymentByOrderId(999L);

        assertThat(result).isEmpty();
    }
}
