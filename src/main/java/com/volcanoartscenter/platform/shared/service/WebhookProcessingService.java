package com.volcanoartscenter.platform.shared.service;

import com.volcanoartscenter.platform.shared.model.Booking;
import com.volcanoartscenter.platform.shared.model.Donation;
import com.volcanoartscenter.platform.shared.model.ShippingOrder;
import com.volcanoartscenter.platform.shared.model.WebhookEventLog;
import com.volcanoartscenter.platform.shared.email.TransactionalEmailService;
import com.volcanoartscenter.platform.shared.notification.NotificationCategory;
import com.volcanoartscenter.platform.shared.notification.NotificationEvent;
import com.volcanoartscenter.platform.shared.payment.OrderStatusHistory;
import com.volcanoartscenter.platform.shared.payment.OrderStatusHistoryRepository;
import com.volcanoartscenter.platform.shared.payment.Payment;
import com.volcanoartscenter.platform.shared.payment.PaymentGateway;
import com.volcanoartscenter.platform.shared.payment.PaymentService;
import com.volcanoartscenter.platform.shared.payment.PaymentSourceType;
import com.volcanoartscenter.platform.shared.repository.BookingRepository;
import com.volcanoartscenter.platform.shared.repository.DonationCampaignRepository;
import com.volcanoartscenter.platform.shared.repository.DonationRepository;
import com.volcanoartscenter.platform.shared.repository.ShippingOrderRepository;
import com.volcanoartscenter.platform.shared.repository.WebhookEventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookProcessingService {

    private final WebhookEventLogRepository webhookEventLogRepository;
    private final ShippingOrderRepository shippingOrderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final BookingRepository bookingRepository;
    private final DonationRepository donationRepository;
    private final DonationCampaignRepository donationCampaignRepository;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionalEmailService transactionalEmailService;

    /**
     * Stripe payment_intent.succeeded handler. Idempotent on externalEventId.
     */
    @Transactional
    public void processStripePaymentSucceeded(String externalEventId, String paymentIntentId) {
        if (alreadyProcessed(externalEventId)) {
            log.info("Idempotency: Stripe event {} already processed.", externalEventId);
            return;
        }
        try {
            Payment payment = paymentService.markCaptured(PaymentGateway.STRIPE_CARD, paymentIntentId);
            applyCapture(payment, OrderStatusHistory.Actor.WEBHOOK,
                    "Stripe payment_intent.succeeded (" + externalEventId + ")");
            recordEvent(externalEventId, "payment_intent.succeeded", "PROCESSED", null);
        } catch (Exception ex) {
            log.error("Failed to process Stripe success event {}", externalEventId, ex);
            recordEvent(externalEventId, "payment_intent.succeeded", "FAILED", ex.getMessage());
            throw ex;
        }
    }

    /**
     * Stripe payment_intent.payment_failed handler. Idempotent on externalEventId.
     */
    @Transactional
    public void processStripePaymentFailed(String externalEventId, String paymentIntentId, String reason) {
        if (alreadyProcessed(externalEventId)) {
            log.info("Idempotency: Stripe event {} already processed.", externalEventId);
            return;
        }
        try {
            Payment payment = paymentService.markFailed(PaymentGateway.STRIPE_CARD, paymentIntentId, reason);
            if (payment.getSourceType() == PaymentSourceType.SHIPPING_ORDER) {
                shippingOrderRepository.findById(payment.getSourceId()).ifPresent(order -> {
                    orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                            .orderId(order.getId())
                            .previousStatus(order.getStatus().name())
                            .newStatus(order.getStatus().name())
                            .previousPaymentStatus(order.getPaymentStatus().name())
                            .newPaymentStatus(order.getPaymentStatus().name())
                            .actor(OrderStatusHistory.Actor.WEBHOOK)
                            .reason("Payment failed: " + (reason == null ? "unknown" : reason))
                            .build());
                    if (payment.getPayerUserId() != null) {
                        eventPublisher.publishEvent(NotificationEvent.forEntity(
                                payment.getPayerUserId(),
                                NotificationCategory.PAYMENT_FAILED,
                                "Payment failed: " + order.getOrderReference(),
                                "We couldn't capture your card for order " + order.getOrderReference()
                                        + ". Please try checking out again.",
                                "/cart",
                                "ShippingOrder",
                                order.getId()));
                    }
                });
            }
            recordEvent(externalEventId, "payment_intent.payment_failed", "PROCESSED", reason);
        } catch (Exception ex) {
            log.error("Failed to process Stripe failure event {}", externalEventId, ex);
            recordEvent(externalEventId, "payment_intent.payment_failed", "FAILED", ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public void processStripeSubscriptionInvoicePaid(String externalEventId, String subscriptionId) {
        if (alreadyProcessed(externalEventId)) {
            log.info("Idempotency: Stripe event {} already processed.", externalEventId);
            return;
        }
        try {
            Payment payment = paymentService.markCaptured(PaymentGateway.STRIPE_CARD, subscriptionId);
            applyCapture(payment, OrderStatusHistory.Actor.WEBHOOK,
                    "Stripe invoice.paid (" + externalEventId + ")");
            recordEvent(externalEventId, "invoice.paid", "PROCESSED", null);
        } catch (Exception ex) {
            log.error("Failed to process Stripe invoice event {}", externalEventId, ex);
            recordEvent(externalEventId, "invoice.paid", "FAILED", ex.getMessage());
            throw ex;
        }
    }

    /** MTN MoMo callback handler. Status: SUCCESSFUL / FAILED / PENDING. */
    @Transactional
    public void processMomoCallback(String externalEventId, String momoReferenceId, String status, String reason) {
        if (alreadyProcessed(externalEventId)) {
            log.info("Idempotency: MoMo event {} already processed.", externalEventId);
            return;
        }
        try {
            String upper = status == null ? "" : status.toUpperCase();
            if ("SUCCESSFUL".equals(upper)) {
                Payment payment = paymentService.markCaptured(PaymentGateway.MTN_MOMO, momoReferenceId);
                applyCapture(payment, OrderStatusHistory.Actor.WEBHOOK,
                        "MTN MoMo callback (" + externalEventId + ")");
                recordMomoEvent(externalEventId, "momo.successful", "PROCESSED", null);
            } else if ("FAILED".equals(upper)) {
                Payment payment = paymentService.markFailed(PaymentGateway.MTN_MOMO, momoReferenceId, reason);
                notifyFailure(payment, reason);
                recordMomoEvent(externalEventId, "momo.failed", "PROCESSED", reason);
            } else {
                log.info("MoMo callback {} reports interim status {} — no state change.", externalEventId, status);
                recordMomoEvent(externalEventId, "momo." + (status == null ? "unknown" : status.toLowerCase()),
                        "IGNORED", null);
            }
        } catch (Exception ex) {
            log.error("Failed to process MoMo event {}", externalEventId, ex);
            recordMomoEvent(externalEventId, "momo.callback", "FAILED", ex.getMessage());
            throw ex;
        }
    }

    /**
     * Staff confirmation of an offline payment (bank transfer, cash). Idempotent
     * via Payment.status check.
     */
    @Transactional
    @com.volcanoartscenter.platform.shared.audit.Audited(action = "PAYMENT_CONFIRMED_OFFLINE", entityType = "Payment")
    public Payment confirmOfflinePayment(PaymentGateway gateway, String gatewayRef, OrderStatusHistory.Actor actor) {
        Payment payment = paymentService.markCaptured(gateway, gatewayRef);
        applyCapture(payment, actor, "Offline payment confirmed by " + actor.name());
        return payment;
    }

    private void applyCapture(Payment payment, OrderStatusHistory.Actor actor, String reason) {
        switch (payment.getSourceType()) {
            case SHIPPING_ORDER -> applyOrderCapture(payment, actor, reason);
            case BOOKING -> applyBookingCapture(payment, reason);
            case DONATION -> applyDonationCapture(payment);
            default -> log.info("Capture for source {} id={} — no domain transition wired.",
                    payment.getSourceType(), payment.getSourceId());
        }
    }

    private void applyBookingCapture(Payment payment, String reason) {
        bookingRepository.findById(payment.getSourceId()).ifPresent(booking -> {
            Booking.PaymentStatus prevPay = booking.getPaymentStatus();
            Booking.BookingStatus prevStatus = booking.getStatus();

            boolean fullyPaid = booking.getTotalPrice() != null
                    && payment.getAmount() != null
                    && payment.getAmount().compareTo(booking.getTotalPrice()) >= 0;
            booking.setPaymentStatus(fullyPaid ? Booking.PaymentStatus.PAID : Booking.PaymentStatus.PARTIAL);
            if (booking.getStatus() == Booking.BookingStatus.PENDING) {
                booking.setStatus(Booking.BookingStatus.CONFIRMED);
                booking.setConfirmedAt(LocalDateTime.now());
            }
            booking.setStripePaymentIntentId(payment.getGatewayRef());
            booking.setPaidAt(LocalDateTime.now());
            boolean sendEmail = booking.getConfirmationEmailSentAt() == null && payment.getEmailSentAt() == null;
            if (sendEmail) {
                transactionalEmailService.sendBookingPaid(booking);
                booking.setConfirmationEmailSentAt(LocalDateTime.now());
                paymentService.markEmailSent(payment);
            }
            bookingRepository.save(booking);

            if (payment.getPayerUserId() != null) {
                eventPublisher.publishEvent(NotificationEvent.forEntity(
                        payment.getPayerUserId(),
                        NotificationCategory.BOOKING_CONFIRMED,
                        "Booking confirmed: " + booking.getBookingReference(),
                        "Payment received. " + reason,
                        "/client/dashboard",
                        "Booking",
                        booking.getId()));
            }
            log.info("Booking {} captured: status {}->{}, payment {}->{}",
                    booking.getBookingReference(), prevStatus, booking.getStatus(),
                    prevPay, booking.getPaymentStatus());
        });
    }

    private void applyDonationCapture(Payment payment) {
        donationRepository.findById(payment.getSourceId()).ifPresent(donation -> {
            if (donation.getStatus() == Donation.DonationStatus.COMPLETED) {
                return;
            }
            donation.setStatus(Donation.DonationStatus.COMPLETED);
            donation.setCompletedAt(LocalDateTime.now());
            donation.setStripePaymentIntentId(payment.getGatewayRef());
            if (payment.getReceiptUrl() != null && !payment.getReceiptUrl().isBlank()) {
                donation.setReceiptUrl(payment.getReceiptUrl());
            }
            if (donation.getCampaign() != null) {
                var campaign = donation.getCampaign();
                campaign.setRaisedAmount((campaign.getRaisedAmount() == null ? BigDecimal.ZERO : campaign.getRaisedAmount())
                        .add(donation.getAmount() == null ? BigDecimal.ZERO : donation.getAmount()));
                campaign.setDonorCount((campaign.getDonorCount() == null ? 0 : campaign.getDonorCount()) + 1);
                donationCampaignRepository.save(campaign);
            }
            boolean sendEmail = donation.getConfirmationEmailSentAt() == null && payment.getEmailSentAt() == null;
            if (sendEmail) {
                transactionalEmailService.sendDonationPaid(donation);
                donation.setConfirmationEmailSentAt(LocalDateTime.now());
                paymentService.markEmailSent(payment);
            }
            donationRepository.save(donation);
            if (payment.getPayerUserId() != null) {
                eventPublisher.publishEvent(NotificationEvent.forEntity(
                        payment.getPayerUserId(),
                        NotificationCategory.DONATION_RECEIVED,
                        "Donation confirmed: " + donation.getReference(),
                        "Payment received. Thank you for supporting Volcano Arts Center conservation work.",
                        "/conservation",
                        "Donation",
                        donation.getId()));
            }
            log.info("Donation {} captured and marked completed.", donation.getReference());
        });
    }

    private void notifyFailure(Payment payment, String reason) {
        if (payment.getPayerUserId() == null) return;
        String entity = payment.getSourceType() == null ? "Payment" : payment.getSourceType().name();
        eventPublisher.publishEvent(NotificationEvent.forEntity(
                payment.getPayerUserId(),
                NotificationCategory.PAYMENT_FAILED,
                "Payment failed",
                reason == null ? "Your payment was declined. Please try again." : reason,
                "/client/dashboard",
                entity,
                payment.getSourceId()));
    }

    private void recordMomoEvent(String externalEventId, String type, String status, String error) {
        if (externalEventId == null || externalEventId.isBlank()) return;
        webhookEventLogRepository.save(WebhookEventLog.builder()
                .externalEventId(externalEventId)
                .provider("MTN_MOMO")
                .eventType(type)
                .status(status)
                .errorMessage(error)
                .build());
    }

    private void applyOrderCapture(Payment payment, OrderStatusHistory.Actor actor, String reason) {
        if (payment.getSourceType() != PaymentSourceType.SHIPPING_ORDER) {
            log.info("Capture for non-order source {} id={} — no order transition required.",
                    payment.getSourceType(), payment.getSourceId());
            return;
        }
        ShippingOrder order = shippingOrderRepository.findById(payment.getSourceId())
                .orElseThrow(() -> new IllegalStateException(
                        "Captured payment references missing order id=" + payment.getSourceId()));

        ShippingOrder.PaymentStatus prevPay = order.getPaymentStatus();
        ShippingOrder.OrderStatus prevStatus = order.getStatus();

        boolean transitioned = false;
        if (order.getPaymentStatus() != ShippingOrder.PaymentStatus.PAID) {
            order.setPaymentStatus(ShippingOrder.PaymentStatus.PAID);
            transitioned = true;
        }
        if (order.getStatus() == ShippingOrder.OrderStatus.PENDING) {
            order.setStatus(ShippingOrder.OrderStatus.PROCESSING);
            transitioned = true;
        }
        if (transitioned) {
            order.setStripePaymentIntentId(payment.getGatewayRef());
            order.setPaidAt(LocalDateTime.now());
            boolean sendEmail = order.getConfirmationEmailSentAt() == null && payment.getEmailSentAt() == null;
            if (sendEmail) {
                transactionalEmailService.sendOrderPaid(order);
                order.setConfirmationEmailSentAt(LocalDateTime.now());
                paymentService.markEmailSent(payment);
            }
            shippingOrderRepository.save(order);
            orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                    .orderId(order.getId())
                    .previousStatus(prevStatus.name())
                    .newStatus(order.getStatus().name())
                    .previousPaymentStatus(prevPay.name())
                    .newPaymentStatus(order.getPaymentStatus().name())
                    .actor(actor)
                    .reason(reason)
                    .build());
            if (payment.getPayerUserId() != null) {
                eventPublisher.publishEvent(NotificationEvent.forEntity(
                        payment.getPayerUserId(),
                        NotificationCategory.ORDER_CONFIRMED,
                        "Order confirmed: " + order.getOrderReference(),
                        "Payment received. We're preparing your order for shipment.",
                        "/client/dashboard",
                        "ShippingOrder",
                        order.getId()));
            }
            log.info("Order {} captured: status {}->{}, payment {}->PAID",
                    order.getOrderReference(), prevStatus, order.getStatus(), prevPay);
        }
    }

    private boolean alreadyProcessed(String externalEventId) {
        return externalEventId != null
                && webhookEventLogRepository.existsByExternalEventId(externalEventId);
    }

    private void recordEvent(String externalEventId, String eventType, String status, String error) {
        if (externalEventId == null || externalEventId.isBlank()) return;
        webhookEventLogRepository.save(WebhookEventLog.builder()
                .externalEventId(externalEventId)
                .provider("STRIPE")
                .eventType(eventType)
                .status(status)
                .errorMessage(error)
                .build());
    }
}
