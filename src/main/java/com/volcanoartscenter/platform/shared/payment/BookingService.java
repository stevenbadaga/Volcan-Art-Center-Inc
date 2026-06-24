package com.volcanoartscenter.platform.shared.payment;

import com.volcanoartscenter.platform.shared.exception.BusinessRuleException;
import com.volcanoartscenter.platform.shared.exception.NotFoundException;
import com.volcanoartscenter.platform.shared.exception.PlatformException;
import com.volcanoartscenter.platform.shared.model.AvailabilitySlot;
import com.volcanoartscenter.platform.shared.model.Booking;
import com.volcanoartscenter.platform.shared.model.Experience;
import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.notification.NotificationCategory;
import com.volcanoartscenter.platform.shared.notification.NotificationEvent;
import com.volcanoartscenter.platform.shared.notification.StaffNotificationPublisher;
import com.volcanoartscenter.platform.shared.repository.BookingRepository;
import com.volcanoartscenter.platform.shared.repository.ExperienceRepository;
import com.volcanoartscenter.platform.shared.service.AvailabilityService;
import com.volcanoartscenter.platform.shared.service.ComplianceService;
import com.volcanoartscenter.platform.shared.service.integration.IntegrationFacadeService;
import com.volcanoartscenter.platform.shared.service.integration.PaymentGatewayService;
import com.volcanoartscenter.platform.shared.service.integration.impl.BankTransferPaymentService;
import com.volcanoartscenter.platform.shared.service.integration.impl.StripePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Booking lifecycle: capacity-aware creation, multi-gateway payment init,
 * cancellation with refund-window policy. Payments are recorded in the
 * polymorphic {@link Payment} ledger with {@code sourceType=BOOKING}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private static final String CURRENCY = "USD";
    private static final BigDecimal HALF = new BigDecimal("0.50");

    private final BookingRepository bookingRepository;
    private final ExperienceRepository experienceRepository;
    private final AvailabilityService availabilityService;
    private final PaymentService paymentService;
    private final StripePaymentService stripePaymentService;
    private final BankTransferPaymentService bankTransferPaymentService;
    private final IntegrationFacadeService integrationFacadeService;
    private final ComplianceService complianceService;
    private final ApplicationEventPublisher eventPublisher;
    private final StaffNotificationPublisher staffNotificationPublisher;

    public record BookingRequest(
            Long experienceId,
            LocalDate preferredDate,
            LocalDate alternativeDate,
            Integer groupSize,
            String preferredLanguage,
            String specialRequests,
            String guestName,
            String guestEmail,
            String guestPhone,
            String guestCountry,
            PaymentGateway paymentMethod,
            String payerMsisdn) {}

    public record BookingResult(
            String bookingRef,
            Long bookingId,
            Long paymentId,
            PaymentGateway gateway,
            String gatewayRef,
            BigDecimal totalPrice,
            BigDecimal depositAmount,
            String currency,
            String clientSecret,
            String publishableKey,
            String paymentInstructions,
            LocalDateTime paymentDueAt) {}

    @Transactional
    public BookingResult createBooking(User user, BookingRequest req) {
        validate(req);
        Experience experience = experienceRepository.findById(req.experienceId())
                .orElseThrow(() -> new NotFoundException("Experience", req.experienceId()));
        if (!Boolean.TRUE.equals(experience.getActive())) {
            throw new BusinessRuleException("EXPERIENCE_INACTIVE",
                    "This experience is no longer accepting bookings.");
        }
        if (req.preferredDate().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("DATE_IN_PAST", "Preferred date must be in the future.");
        }

        int groupSize = enforceGroupBounds(experience, req.groupSize());
        BigDecimal totalPrice = computePrice(experience, groupSize);
        boolean depositRequired = experience.getBookingType() == Experience.BookingType.INQUIRY;
        BigDecimal depositAmount = depositRequired
                ? totalPrice.multiply(HALF).setScale(2, java.math.RoundingMode.HALF_UP)
                : totalPrice;

        availabilityService.applyBookingToSlot(experience, req.preferredDate(), groupSize);

        String guestName = firstNonBlank(req.guestName(), user == null ? null : user.getFullName(),
                "Guest");
        String guestEmail = normalizeEmail(firstNonBlank(req.guestEmail(),
                user == null ? null : user.getEmail(), null));
        if (guestEmail == null) {
            throw new BusinessRuleException("EMAIL_REQUIRED", "A contact email is required.");
        }

        Booking booking = Booking.builder()
                .bookingReference("BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT))
                .user(user)
                .experience(experience)
                .guestName(guestName)
                .guestEmail(guestEmail)
                .guestPhone(firstNonBlank(req.guestPhone(), user == null ? null : user.getPhone(), null))
                .guestCountry(firstNonBlank(req.guestCountry(), user == null ? null : user.getCountry(), null))
                .preferredDate(req.preferredDate())
                .alternativeDate(req.alternativeDate())
                .groupSize(groupSize)
                .preferredLanguage(req.preferredLanguage() == null ? "English" : req.preferredLanguage())
                .specialRequests(req.specialRequests())
                .totalPrice(totalPrice)
                .depositAmount(depositAmount)
                .depositRequired(depositRequired)
                .paymentMethod(req.paymentMethod().name())
                .paymentStatus(Booking.PaymentStatus.UNPAID)
                .status(Booking.BookingStatus.PENDING)
                .paymentDueAt(LocalDateTime.now().plusDays(2))
                .build();

        Booking saved = bookingRepository.save(booking);

        PaymentDispatch dispatch = dispatchPayment(req.paymentMethod(), saved, depositAmount, req);
        Payment payment = paymentService.createPending(
                req.paymentMethod(),
                dispatch.gatewayRef(),
                PaymentSourceType.BOOKING,
                saved.getId(),
                depositAmount,
                CURRENCY,
                user == null ? null : user.getId(),
                guestEmail);

        saved.setPaymentReference(dispatch.gatewayRef());
        if (req.paymentMethod() == PaymentGateway.STRIPE_CARD) {
            saved.setStripePaymentIntentId(dispatch.gatewayRef());
        }
        bookingRepository.save(saved);

        complianceService.recordConsent(guestEmail, "BOOKING_TERMS", true, "api-booking");
        complianceService.audit(guestEmail, "BOOKING_CREATED", "Booking", saved.getId(),
                "ref=" + saved.getBookingReference()
                        + ", method=" + req.paymentMethod()
                        + ", deposit=" + depositAmount);

        if (user != null) {
            eventPublisher.publishEvent(NotificationEvent.forEntity(
                    user.getId(),
                    NotificationCategory.BOOKING_RECEIVED,
                    "Booking received: " + saved.getBookingReference(),
                    "We've received your booking for " + experience.getTitle()
                            + " on " + saved.getPreferredDate()
                            + ". Awaiting payment confirmation.",
                    "/client/dashboard",
                    "Booking",
                    saved.getId()));
        }

        staffNotificationPublisher.notifyAllStaff(
                NotificationCategory.STAFF_NEW_BOOKING,
                "New tour booking: " + saved.getBookingReference(),
                experience.getTitle() + " on " + saved.getPreferredDate()
                        + " for " + saved.getGroupSize() + " guest(s)",
                "/admin/ops/bookings",
                "Booking",
                saved.getId());

        return new BookingResult(
                saved.getBookingReference(),
                saved.getId(),
                payment.getId(),
                req.paymentMethod(),
                dispatch.gatewayRef(),
                totalPrice,
                depositAmount,
                CURRENCY,
                dispatch.clientSecret(),
                dispatch.publishableKey(),
                dispatch.instructions(),
                saved.getPaymentDueAt());
    }

    @Transactional
    @com.volcanoartscenter.platform.shared.audit.Audited(action = "BOOKING_CANCELLED", entityType = "Booking")
    public Booking cancelBooking(User user, String bookingRef, String reason) {
        Booking booking = bookingRepository.findByBookingReference(bookingRef)
                .orElseThrow(() -> new NotFoundException("Booking", bookingRef));
        if (user != null && booking.getUser() != null && !booking.getUser().getId().equals(user.getId())) {
            throw new PlatformException("FORBIDDEN", "Booking does not belong to this account.",
                    HttpStatus.FORBIDDEN);
        }
        if (booking.getStatus() == Booking.BookingStatus.CANCELLED) {
            return booking;
        }
        if (booking.getStatus() == Booking.BookingStatus.COMPLETED) {
            throw new BusinessRuleException("ALREADY_COMPLETED",
                    "Completed bookings cannot be cancelled.");
        }

        long daysToBooking = ChronoUnit.DAYS.between(LocalDate.now(), booking.getPreferredDate());
        RefundPolicy policy = decideRefund(booking, daysToBooking);

        availabilityService.releaseBookingFromSlot(booking.getExperience(),
                booking.getPreferredDate(), booking.getGroupSize());

        booking.setStatus(Booking.BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancellationReason(reason);
        bookingRepository.save(booking);

        complianceService.audit(booking.getGuestEmail(), "BOOKING_CANCELLED", "Booking",
                booking.getId(),
                "policy=" + policy.label() + ", reason=" + (reason == null ? "" : reason));
        log.info("Booking {} cancelled — refund policy={}",
                booking.getBookingReference(), policy.label());
        return booking;
    }

    private PaymentDispatch dispatchPayment(PaymentGateway method, Booking booking,
                                            BigDecimal amount, BookingRequest req) {
        return switch (method) {
            case STRIPE_CARD -> {
                StripePaymentService.CardIntent intent = stripePaymentService.initializeCardIntent(
                        booking.getBookingReference(), amount, CURRENCY,
                        Map.of("bookingRef", booking.getBookingReference(),
                               "bookingId", String.valueOf(booking.getId()),
                               "email", booking.getGuestEmail()));
                yield new PaymentDispatch(intent.id(), intent.clientSecret(),
                        stripePaymentService.publishableKey(),
                        "Confirm card on the client to complete this booking.");
            }
            case MTN_MOMO -> {
                if (req.payerMsisdn() == null || req.payerMsisdn().isBlank()) {
                    throw new BusinessRuleException("MOMO_MSISDN_REQUIRED",
                            "MTN MoMo requires a payer phone number.");
                }
                PaymentGatewayService.PaymentResult result = integrationFacadeService.initializePayment(
                        "MTN_MOMO", booking.getBookingReference(), amount, CURRENCY,
                        Map.of("msisdn", req.payerMsisdn(),
                               "email", booking.getGuestEmail()));
                yield new PaymentDispatch(result.externalReference(), null, null,
                        "Approve the prompt on " + req.payerMsisdn() + " to complete this booking.");
            }
            case BANK_TRANSFER -> {
                PaymentGatewayService.PaymentResult result = bankTransferPaymentService.initialize(
                        booking.getBookingReference(), amount, CURRENCY, Map.of());
                yield new PaymentDispatch(result.externalReference(), null, null, result.message());
            }
            case MANUAL -> throw new BusinessRuleException("MANUAL_NOT_SELF_SERVE",
                    "MANUAL payments are recorded by staff only.");
        };
    }

    private record PaymentDispatch(String gatewayRef, String clientSecret,
                                   String publishableKey, String instructions) {}

    private record RefundPolicy(BigDecimal percentage, String label) {}

    private RefundPolicy decideRefund(Booking booking, long daysToBooking) {
        if (booking.getPaymentStatus() != Booking.PaymentStatus.PAID
                && booking.getPaymentStatus() != Booking.PaymentStatus.PARTIAL) {
            return new RefundPolicy(BigDecimal.ZERO, "no-payment-captured");
        }
        if (daysToBooking >= 7) return new RefundPolicy(BigDecimal.ONE, "full-refund");
        if (daysToBooking >= 2) return new RefundPolicy(HALF, "50pct-refund");
        return new RefundPolicy(BigDecimal.ZERO, "no-refund-late-cancellation");
    }

    private void validate(BookingRequest req) {
        if (req == null) throw new BusinessRuleException("VALIDATION_FAILED", "Booking payload is required.");
        if (req.experienceId() == null) throw new BusinessRuleException("VALIDATION_FAILED", "experienceId is required.");
        if (req.preferredDate() == null) throw new BusinessRuleException("VALIDATION_FAILED", "preferredDate is required.");
        if (req.paymentMethod() == null) throw new BusinessRuleException("VALIDATION_FAILED", "paymentMethod is required.");
    }

    private int enforceGroupBounds(Experience exp, Integer requested) {
        int size = requested == null ? 1 : Math.max(1, requested);
        int min = exp.getMinGroupSize() == null ? 1 : exp.getMinGroupSize();
        int max = exp.getMaxGroupSize() == null ? 15 : exp.getMaxGroupSize();
        if (size < min) {
            throw new BusinessRuleException("GROUP_TOO_SMALL",
                    "Minimum group size is " + min + ".");
        }
        if (size > max) {
            throw new BusinessRuleException("GROUP_TOO_LARGE",
                    "Maximum group size is " + max + ".");
        }
        return size;
    }

    private BigDecimal computePrice(Experience exp, int groupSize) {
        if (exp.getGroupPrice() != null && exp.getGroupPrice().compareTo(BigDecimal.ZERO) > 0) {
            return exp.getGroupPrice();
        }
        if (exp.getPricePerPerson() != null && exp.getPricePerPerson().compareTo(BigDecimal.ZERO) > 0) {
            return exp.getPricePerPerson().multiply(BigDecimal.valueOf(groupSize));
        }
        throw new BusinessRuleException("PRICE_UNCONFIGURED",
                "This experience does not have a price configured.");
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c;
        }
        return null;
    }

    private static String normalizeEmail(String s) {
        return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
    }
}
