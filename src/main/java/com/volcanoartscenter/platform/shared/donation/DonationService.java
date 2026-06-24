package com.volcanoartscenter.platform.shared.donation;

import com.volcanoartscenter.platform.shared.exception.BusinessRuleException;
import com.volcanoartscenter.platform.shared.exception.NotFoundException;
import com.volcanoartscenter.platform.shared.model.Donation;
import com.volcanoartscenter.platform.shared.model.DonationCampaign;
import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.notification.NotificationCategory;
import com.volcanoartscenter.platform.shared.notification.NotificationEvent;
import com.volcanoartscenter.platform.shared.payment.Payment;
import com.volcanoartscenter.platform.shared.payment.PaymentGateway;
import com.volcanoartscenter.platform.shared.payment.PaymentService;
import com.volcanoartscenter.platform.shared.payment.PaymentSourceType;
import com.volcanoartscenter.platform.shared.reference.ReferenceNumberService;
import com.volcanoartscenter.platform.shared.repository.DonationCampaignRepository;
import com.volcanoartscenter.platform.shared.repository.DonationRepository;
import com.volcanoartscenter.platform.shared.service.integration.impl.StripePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Donation orchestration: VAC reference allocation, impact-tier label, payment
 * intent for one-off donations, Stripe Subscription handle for recurring.
 *
 * <p>Subscription renewals arrive as {@code invoice.paid} Stripe events and are
 * handled in {@link com.volcanoartscenter.platform.shared.service.WebhookProcessingService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DonationService {

    private final DonationRepository donationRepository;
    private final DonationCampaignRepository donationCampaignRepository;
    private final ReferenceNumberService referenceNumberService;
    private final ImpactTierService impactTierService;
    private final StripePaymentService stripePaymentService;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;

    public record DonationRequest(
            Long campaignId,
            BigDecimal amount,
            String currency,
            Donation.DonationPurpose purpose,
            String donorName,
            String donorEmail,
            String donorCountry,
            String message,
            Boolean anonymous,
            Boolean recurring,
            Donation.RecurringFrequency recurringFrequency) {}

    public record DonationResult(
            String reference,
            Long donationId,
            BigDecimal amount,
            String currency,
            String impactTier,
            String clientSecret,
            String publishableKey,
            String stripeSubscriptionId,
            boolean recurring) {}

    @Transactional
    public DonationResult recordDonation(User user, DonationRequest req) {
        validate(req);
        DonationCampaign campaign = req.campaignId() == null ? null
                : donationCampaignRepository.findById(req.campaignId())
                        .orElseThrow(() -> new NotFoundException("DonationCampaign", req.campaignId()));
        String currency = req.currency() == null || req.currency().isBlank() ? "USD" : req.currency().toUpperCase(Locale.ROOT);
        String donorEmail = normalizeEmail(req.donorEmail() == null && user != null ? user.getEmail() : req.donorEmail());
        if (donorEmail == null) {
            throw new BusinessRuleException("EMAIL_REQUIRED", "A donor email is required.");
        }
        String donorName = req.donorName() == null && user != null ? user.getFullName() : req.donorName();
        boolean recurring = Boolean.TRUE.equals(req.recurring());
        Donation.RecurringFrequency frequency = req.recurringFrequency() == null && recurring
                ? Donation.RecurringFrequency.MONTHLY : req.recurringFrequency();

        String impactTier = impactTierService.labelFor(campaign, req.amount());
        String reference = referenceNumberService.next(ReferenceNumberService.SCOPE_DONATION);

        Donation donation = Donation.builder()
                .reference(reference)
                .donorName(donorName == null ? "Anonymous" : donorName)
                .donorEmail(donorEmail)
                .donorCountry(req.donorCountry())
                .user(user)
                .campaign(campaign)
                .amount(req.amount())
                .currency(currency)
                .purpose(req.purpose() == null ? Donation.DonationPurpose.GENERAL : req.purpose())
                .message(req.message())
                .isRecurring(recurring)
                .recurringFrequency(frequency)
                .paymentMethod("STRIPE_CARD")
                .status(Donation.DonationStatus.PENDING)
                .isAnonymous(Boolean.TRUE.equals(req.anonymous()))
                .impactTierLabel(impactTier)
                .build();

        Donation saved = donationRepository.save(donation);

        String clientSecret = null;
        String publishableKey = stripePaymentService.publishableKey();
        String subscriptionId = null;

        if (recurring) {
            StripePaymentService.SubscriptionHandle sub = stripePaymentService.createDonationSubscription(
                    donorEmail, donorName, saved.getAmount(), currency,
                    intervalFor(frequency), saved.getReference());
            subscriptionId = sub.subscriptionId();
            clientSecret = sub.clientSecret();
            saved.setStripeCustomerId(sub.customerId());
            saved.setStripeSubscriptionId(subscriptionId);
            saved.setTransactionId(subscriptionId);
            donationRepository.save(saved);

            paymentService.createPending(PaymentGateway.STRIPE_CARD, subscriptionId,
                    PaymentSourceType.DONATION, saved.getId(), saved.getAmount(), currency,
                    user == null ? null : user.getId(), donorEmail);
        } else {
            StripePaymentService.CardIntent intent = stripePaymentService.initializeCardIntent(
                    saved.getReference(), saved.getAmount(), currency,
                    Map.of("donationId", String.valueOf(saved.getId()),
                           "donationRef", saved.getReference(),
                           "email", donorEmail));
            clientSecret = intent.clientSecret();
            saved.setTransactionId(intent.id());
            saved.setStripePaymentIntentId(intent.id());
            donationRepository.save(saved);

            paymentService.createPending(PaymentGateway.STRIPE_CARD, intent.id(),
                    PaymentSourceType.DONATION, saved.getId(), saved.getAmount(), currency,
                    user == null ? null : user.getId(), donorEmail);
        }

        if (user != null) {
            eventPublisher.publishEvent(NotificationEvent.forEntity(
                    user.getId(),
                    NotificationCategory.DONATION_RECEIVED,
                    "Thank you for your donation: " + saved.getReference(),
                    "We've recorded your "
                            + (recurring ? "recurring " + (frequency == null ? "monthly" : frequency.name().toLowerCase()) + " " : "")
                            + "donation of " + saved.getAmount() + " " + currency
                            + (impactTier == null ? "." : " — impact tier: " + impactTier + "."),
                    "/client/dashboard",
                    "Donation",
                    saved.getId()));
        }

        return new DonationResult(saved.getReference(), saved.getId(),
                saved.getAmount(), currency, impactTier,
                clientSecret, publishableKey, subscriptionId, recurring);
    }

    public Optional<Donation> findByReference(String reference) {
        return donationRepository.findByReference(reference);
    }

    private String intervalFor(Donation.RecurringFrequency frequency) {
        return switch (frequency == null ? Donation.RecurringFrequency.MONTHLY : frequency) {
            case MONTHLY -> "month";
            case QUARTERLY -> "quarter";
            case ANNUALLY -> "year";
        };
    }

    private void validate(DonationRequest req) {
        if (req == null) throw new BusinessRuleException("VALIDATION_FAILED", "Donation payload is required.");
        if (req.amount() == null || req.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("AMOUNT_INVALID", "Donation amount must be greater than zero.");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
