package com.volcanoartscenter.platform.shared.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 30)
    private String bookingReference;

    // Guest info (may or may not be a registered user)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "guest_name", nullable = false, length = 150)
    private String guestName;

    @Column(name = "guest_email", nullable = false, length = 150)
    private String guestEmail;

    @Column(name = "guest_phone", length = 30)
    private String guestPhone;

    @Column(name = "guest_country", length = 100)
    private String guestCountry;

    // Linked experience
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experience_id")
    private Experience experience;

    // Booking details
    @Column(name = "preferred_date", nullable = false)
    private LocalDate preferredDate;

    @Column(name = "alternative_date")
    private LocalDate alternativeDate;

    @Column(name = "group_size", nullable = false)
    @Builder.Default
    private Integer groupSize = 1;

    @Column(name = "special_requests", columnDefinition = "TEXT")
    private String specialRequests;

    @Column(name = "preferred_language", length = 30)
    @Builder.Default
    private String preferredLanguage = "English";

    // Status tracking
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    // Financial
    @Column(name = "total_price", precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    // Tour operator reference
    @Column(name = "tour_operator_name", length = 200)
    private String tourOperatorName;

    @Column(name = "tour_operator_email", length = 150)
    private String tourOperatorEmail;

    // Internal notes
    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    // Deposit / payment policy
    @Column(name = "deposit_amount", precision = 10, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "deposit_required", nullable = false)
    @Builder.Default
    private Boolean depositRequired = Boolean.FALSE;

    @Column(name = "payment_due_at")
    private LocalDateTime paymentDueAt;

    @Column(name = "payment_reference", length = 120)
    private String paymentReference;

    @Column(name = "stripe_checkout_session_id", length = 200)
    private String stripeCheckoutSessionId;

    @Column(name = "stripe_payment_intent_id", length = 200)
    private String stripePaymentIntentId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "confirmation_email_sent_at")
    private LocalDateTime confirmationEmailSentAt;

    // Cancellation
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    // Timestamps
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum BookingStatus {
        PENDING,      // Inquiry submitted
        CONFIRMED,    // Accepted by staff
        CANCELLED,    // Cancelled
        COMPLETED     // Experience delivered
    }

    public enum PaymentStatus {
        UNPAID,
        PARTIAL,
        PAID,
        REFUNDED
    }
}
