package com.volcanoartscenter.platform.shared.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "donations")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Donation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "donor_name", nullable = false, length = 200)
    private String donorName;

    @Column(name = "donor_email", nullable = false, length = 150)
    private String donorEmail;

    @Column(name = "donor_country", length = 100)
    private String donorCountry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private DonationCampaign campaign;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    // Donation purpose
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DonationPurpose purpose = DonationPurpose.GENERAL;

    @Column(columnDefinition = "TEXT")
    private String message;

    // Recurring
    @Column(name = "is_recurring")
    @Builder.Default
    private Boolean isRecurring = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurring_frequency", length = 20)
    private RecurringFrequency recurringFrequency;

    // Payment
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "transaction_id", length = 200)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DonationStatus status = DonationStatus.PENDING;

    // Anonymous
    @Column(name = "is_anonymous")
    @Builder.Default
    private Boolean isAnonymous = false;

    // Phase 8: VAC-YYYY-NNNNN reference + impact + Stripe linkage
    @Column(unique = true, length = 40)
    private String reference;

    @Column(name = "impact_tier_label", length = 200)
    private String impactTierLabel;

    @Column(name = "stripe_customer_id", length = 120)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 120)
    private String stripeSubscriptionId;

    @Column(name = "stripe_payment_intent_id", length = 200)
    private String stripePaymentIntentId;

    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    @Column(name = "confirmation_email_sent_at")
    private LocalDateTime confirmationEmailSentAt;

    // Timestamps
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum DonationPurpose {
        GENERAL,
        REFORESTATION,
        COMMUNITY_EDUCATION,
        YOUTH_EMPOWERMENT,
        CONSERVATION
    }

    public enum RecurringFrequency {
        MONTHLY,
        QUARTERLY,
        ANNUALLY
    }

    public enum DonationStatus {
        PENDING,
        COMPLETED,
        FAILED,
        REFUNDED
    }
}
