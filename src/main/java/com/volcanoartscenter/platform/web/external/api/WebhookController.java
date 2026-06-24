package com.volcanoartscenter.platform.web.external.api;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.volcanoartscenter.platform.shared.service.WebhookProcessingService;
import com.volcanoartscenter.platform.shared.service.integration.impl.StripePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookProcessingService webhookProcessingService;
    private final StripePaymentService stripePaymentService;

    /**
     * Stripe webhook entry. The body is read as raw bytes (Stripe signs the exact payload),
     * decoded as UTF-8, and verified via {@link Webhook#constructEvent}. On signature failure
     * we return 400 so Stripe retries; on processing failure we return 500.
     */
    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            @RequestBody byte[] rawBody) {
        String payload = rawBody == null ? "" : new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);
        String secret = stripePaymentService.webhookSecret();

        if (secret == null || secret.isBlank()) {
            log.error("Stripe webhook hit but webhook secret is not configured. Rejecting.");
            return ResponseEntity.status(503).body("webhook secret not configured");
        }
        if (signature == null || signature.isBlank()) {
            log.warn("Stripe webhook missing Stripe-Signature header.");
            return ResponseEntity.badRequest().body("missing signature");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, secret);
        } catch (SignatureVerificationException ex) {
            log.warn("Stripe webhook signature verification failed: {}", ex.getMessage());
            return ResponseEntity.badRequest().body("invalid signature");
        } catch (Exception ex) {
            log.error("Stripe webhook payload parse error", ex);
            return ResponseEntity.badRequest().body("invalid payload");
        }

        log.info("Stripe webhook accepted: id={} type={}", event.getId(), event.getType());

        try {
            switch (event.getType()) {
                case "payment_intent.succeeded" -> {
                    PaymentIntent intent = extractPaymentIntent(event);
                    if (intent != null) {
                        webhookProcessingService.processStripePaymentSucceeded(event.getId(), intent.getId());
                    }
                }
                case "payment_intent.payment_failed" -> {
                    PaymentIntent intent = extractPaymentIntent(event);
                    if (intent != null) {
                        String reason = intent.getLastPaymentError() != null
                                ? intent.getLastPaymentError().getMessage()
                                : "Payment failed";
                        webhookProcessingService.processStripePaymentFailed(event.getId(), intent.getId(), reason);
                    }
                }
                case "checkout.session.completed" -> {
                    Session session = extract(event, Session.class);
                    if (session != null && session.getPaymentIntent() != null) {
                        webhookProcessingService.processStripePaymentSucceeded(event.getId(), session.getPaymentIntent());
                    }
                }
                case "invoice.paid", "invoice.payment_succeeded" -> {
                    Invoice invoice = extract(event, Invoice.class);
                    if (invoice != null && invoice.getSubscription() != null) {
                        webhookProcessingService.processStripeSubscriptionInvoicePaid(event.getId(), invoice.getSubscription());
                    }
                }
                default -> log.debug("Stripe event {} ignored.", event.getType());
            }
            return ResponseEntity.ok("ok");
        } catch (Exception ex) {
            log.error("Stripe webhook processing failed for event {}", event.getId(), ex);
            return ResponseEntity.status(500).body("processing failed");
        }
    }

    private PaymentIntent extractPaymentIntent(Event event) {
        PaymentIntent intent = extract(event, PaymentIntent.class);
        if (intent != null) {
            return intent;
        }
        log.warn("Stripe event {} did not contain a PaymentIntent payload.", event.getId());
        return null;
    }

    private <T extends StripeObject> T extract(Event event, Class<T> type) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject obj = deserializer.getObject().orElse(null);
        if (type.isInstance(obj)) {
            return type.cast(obj);
        }
        return null;
    }
}
