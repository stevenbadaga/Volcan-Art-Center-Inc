package com.volcanoartscenter.platform.shared.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment createPending(PaymentGateway gateway, String gatewayRef,
                                 PaymentSourceType sourceType, Long sourceId,
                                 BigDecimal amount, String currency,
                                 Long payerUserId, String payerEmail) {
        Payment payment = Payment.builder()
                .gateway(gateway)
                .gatewayRef(gatewayRef)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.PENDING)
                .payerUserId(payerUserId)
                .payerEmail(payerEmail)
                .build();
        return paymentRepository.save(payment);
    }

    public Optional<Payment> findByGatewayRef(PaymentGateway gateway, String gatewayRef) {
        return paymentRepository.findByGatewayAndGatewayRef(gateway, gatewayRef);
    }

    @Transactional
    public Payment markCaptured(PaymentGateway gateway, String gatewayRef) {
        Payment payment = paymentRepository.findByGatewayAndGatewayRef(gateway, gatewayRef)
                .orElseThrow(() -> new IllegalStateException(
                        "No payment recorded for " + gateway + ":" + gatewayRef));
        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            payment.setStatus(PaymentStatus.CAPTURED);
            payment.setCapturedAt(LocalDateTime.now());
            payment.setFailureReason(null);
        }
        return payment;
    }

    @Transactional
    public void markEmailSent(Payment payment) {
        if (payment != null && payment.getEmailSentAt() == null) {
            payment.setEmailSentAt(LocalDateTime.now());
        }
    }

    @Transactional
    public void attachReceipt(Payment payment, String receiptUrl) {
        if (payment != null && receiptUrl != null && !receiptUrl.isBlank()) {
            payment.setReceiptUrl(receiptUrl);
        }
    }

    @Transactional
    public Payment markFailed(PaymentGateway gateway, String gatewayRef, String reason) {
        Payment payment = paymentRepository.findByGatewayAndGatewayRef(gateway, gatewayRef)
                .orElseThrow(() -> new IllegalStateException(
                        "No payment recorded for " + gateway + ":" + gatewayRef));
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        return payment;
    }
}
