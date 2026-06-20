package com.volcanoartscenter.platform.web.internal.opsmanager.service;

import com.volcanoartscenter.platform.shared.model.AvailabilitySlot;
import com.volcanoartscenter.platform.shared.model.BlackoutDate;
import com.volcanoartscenter.platform.shared.model.Booking;
import com.volcanoartscenter.platform.shared.model.ContactInquiry;
import com.volcanoartscenter.platform.shared.model.Experience;
import com.volcanoartscenter.platform.shared.model.ShippingOrder;
import com.volcanoartscenter.platform.shared.model.TalentApplication;
import com.volcanoartscenter.platform.shared.model.TourOperatorRequest;
import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.repository.AvailabilitySlotRepository;
import com.volcanoartscenter.platform.shared.repository.BlackoutDateRepository;
import com.volcanoartscenter.platform.shared.repository.BookingRepository;
import com.volcanoartscenter.platform.shared.repository.ContactInquiryRepository;
import com.volcanoartscenter.platform.shared.repository.DonationRepository;
import com.volcanoartscenter.platform.shared.repository.ExperienceRepository;
import com.volcanoartscenter.platform.shared.repository.ShippingOrderRepository;
import com.volcanoartscenter.platform.shared.repository.TalentApplicationRepository;
import com.volcanoartscenter.platform.shared.repository.TourOperatorRequestRepository;
import com.volcanoartscenter.platform.shared.repository.UserRepository;
import com.volcanoartscenter.platform.shared.service.AvailabilityService;
import com.volcanoartscenter.platform.shared.service.ComplianceService;
import com.volcanoartscenter.platform.shared.service.integration.IntegrationFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpsManagerService {

    private final BookingRepository bookingRepository;
    private final DonationRepository donationRepository;
    private final TalentApplicationRepository talentApplicationRepository;
    private final ShippingOrderRepository shippingOrderRepository;
    private final ContactInquiryRepository contactInquiryRepository;
    private final TourOperatorRequestRepository tourOperatorRequestRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final ExperienceRepository experienceRepository;
    private final BlackoutDateRepository blackoutDateRepository;
    private final UserRepository userRepository;
    private final com.volcanoartscenter.platform.shared.repository.ProductRepository productRepository;
    private final AvailabilityService availabilityService;
    private final ComplianceService complianceService;
    private final IntegrationFacadeService integrationFacadeService;
    private final com.volcanoartscenter.platform.shared.service.NotificationService notificationService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public long totalBookings() { return bookingRepository.count(); }
    public long totalOrders() { return shippingOrderRepository.count(); }
    public long totalInquiries() { return contactInquiryRepository.count(); }

    public Booking getBookingByReference(String reference) {
        return bookingRepository.findByBookingReference(reference)
                .orElseThrow(() -> new com.volcanoartscenter.platform.shared.exception.NotFoundException("Booking not found: " + reference));
    }

    public long pendingTalentApplications() {
        return talentApplicationRepository.findAll().stream()
                .filter(a -> a.getStatus() == TalentApplication.ApplicationStatus.PENDING || a.getStatus() == TalentApplication.ApplicationStatus.AWAITING_INFO)
                .count();
    }

    public List<Booking> listBookings() {
        return bookingRepository.findAll().stream()
                .sorted(Comparator.comparing(Booking::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    @Transactional
    public Booking updateBookingStatus(Long id, Booking.BookingStatus status, Booking.PaymentStatus paymentStatus, String adminNotes, String notifyChannel, String actorEmail) {
        Booking booking = bookingRepository.findById(id).orElseThrow();
        Booking.BookingStatus previousStatus = booking.getStatus();
        boolean wasCapacityHolding = previousStatus != Booking.BookingStatus.CANCELLED;
        boolean shouldHoldCapacity = status != Booking.BookingStatus.CANCELLED;

        if (wasCapacityHolding && !shouldHoldCapacity) {
            availabilityService.releaseBookingFromSlot(
                    booking.getExperience(),
                    booking.getPreferredDate(),
                    booking.getGroupSize() == null ? 1 : booking.getGroupSize()
            );
        } else if (!wasCapacityHolding && shouldHoldCapacity) {
            availabilityService.applyBookingToSlot(
                    booking.getExperience(),
                    booking.getPreferredDate(),
                    booking.getGroupSize() == null ? 1 : booking.getGroupSize()
            );
        }

        booking.setStatus(status);
        if (paymentStatus != null) {
            booking.setPaymentStatus(paymentStatus);
        }
        booking.setAdminNotes(adminNotes);
        if ((status == Booking.BookingStatus.CONFIRMED || status == Booking.BookingStatus.COMPLETED) && booking.getConfirmedAt() == null) {
            booking.setConfirmedAt(LocalDateTime.now());
        }
        complianceService.audit(actorEmail, "OPS_BOOKING_UPDATED", "Booking", id, "Status=" + status + ", Payment=" + paymentStatus + ", notes=" + adminNotes);
        sendNotification(notifyChannel, booking.getGuestEmail(), booking.getGuestPhone(),
                "Booking update: " + booking.getBookingReference(),
                "Your booking " + booking.getBookingReference() + " is now " + status + ".");
        return booking;
    }

    public List<?> listDonations() {
        return donationRepository.findAll().stream()
                .sorted(Comparator.comparing(d -> ((com.volcanoartscenter.platform.shared.model.Donation) d).getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    public List<TalentApplication> listTalentApplications() {
        return talentApplicationRepository.findAll().stream()
                .sorted(Comparator.comparing(TalentApplication::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    @Transactional
    public TalentApplication updateTalentApplicationStatus(Long id, TalentApplication.ApplicationStatus status, String adminNotes, String notifyChannel, String actorEmail) {
        TalentApplication application = talentApplicationRepository.findById(id).orElseThrow();
        application.setStatus(status);
        application.setAdminNotes(adminNotes);
        application.setReviewedAt(LocalDateTime.now());
        complianceService.audit(actorEmail, "OPS_TALENT_APPLICATION_UPDATED", "TalentApplication", id, "Status=" + status);
        if (notifyChannel != null && !notifyChannel.isBlank()) {
            application.setLastNotifiedChannel(notifyChannel.trim().toUpperCase(Locale.ROOT));
            application.setLastNotifiedAt(LocalDateTime.now());
        }

        // Domain Event decoupling for approvals
        if (status == TalentApplication.ApplicationStatus.APPROVED) {
            eventPublisher.publishEvent(new com.volcanoartscenter.platform.shared.event.TalentApplicationApprovedEvent(this, application));
        } else {
            sendNotification(notifyChannel, application.getEmail(), application.getPhone(),
                    "Talent application update",
                    "Your application status is now " + status + ".");
        }
        return application;
    }

    public List<ShippingOrder> listShippingOrders() {
        return shippingOrderRepository.findAll().stream()
                .sorted(Comparator.comparing(ShippingOrder::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    @Transactional
    public ShippingOrder updateShippingOrderStatus(Long id, ShippingOrder.OrderStatus status, String trackingNumber, String adminNotes, String notifyChannel, String actorEmail) {
        ShippingOrder order = shippingOrderRepository.findById(id).orElseThrow();
        order.setStatus(status);
        order.setTrackingNumber(trackingNumber);
        order.setAdminNotes(adminNotes);
        if (status == ShippingOrder.OrderStatus.SHIPPED || status == ShippingOrder.OrderStatus.IN_TRANSIT) {
            order.setShippedAt(order.getShippedAt() == null ? LocalDateTime.now() : order.getShippedAt());
        }
        if (status == ShippingOrder.OrderStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
        }
        complianceService.audit(actorEmail, "OPS_ORDER_UPDATED", "ShippingOrder", id, "Status=" + status + ", tracking=" + trackingNumber);
        sendNotification(notifyChannel, order.getRecipientEmail(), order.getRecipientPhone(),
                "Order update: " + order.getOrderReference(),
                "Your order " + order.getOrderReference() + " is now " + status + ".");
        return order;
    }

    @Transactional
    public ShippingOrder createManualOrder(Long userId, Long productId, Integer quantity, BigDecimal totalAmount, ShippingOrder.OrderStatus status, ShippingOrder.PaymentStatus paymentStatus, String adminNotes, String actorEmail) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        com.volcanoartscenter.platform.shared.model.Product product = productRepository.findById(productId).orElseThrow(() -> new IllegalArgumentException("Product not found"));

        ShippingOrder order = new ShippingOrder();
        order.setOrderReference("MAN-" + System.currentTimeMillis() + "-" + (int)(Math.random()*1000));
        order.setUser(user);
        order.setRecipientName(user.getFullName());
        order.setRecipientEmail(user.getEmail());
        order.setRecipientPhone(user.getPhone() != null ? user.getPhone() : "N/A");
        order.setProduct(product);
        order.setQuantity(quantity != null ? quantity : 1);
        order.setProductTotal(totalAmount);
        order.setTotalAmount(totalAmount);
        order.setStatus(status);
        order.setPaymentStatus(paymentStatus);
        order.setAdminNotes(adminNotes);
        order.setCreatedAt(LocalDateTime.now());

        ShippingOrder saved = shippingOrderRepository.save(order);
        complianceService.audit(actorEmail, "OPS_MANUAL_ORDER_CREATED", "ShippingOrder", saved.getId(), "Ref=" + saved.getOrderReference());
        return saved;
    }

    public List<ContactInquiry> listContactInquiries() {
        return contactInquiryRepository.findAll().stream()
                .sorted(Comparator.comparing(ContactInquiry::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    @Transactional
    public ContactInquiry updateInquiryStatus(Long id, ContactInquiry.InquiryStatus status, String notifyChannel, String actorEmail) {
        ContactInquiry inquiry = contactInquiryRepository.findById(id).orElseThrow();
        inquiry.setStatus(status);
        complianceService.audit(actorEmail, "OPS_INQUIRY_UPDATED", "ContactInquiry", id, "Status=" + status);
        sendNotification(notifyChannel, inquiry.getEmail(), inquiry.getPhone(),
                "Inquiry update",
                "Your inquiry status is now " + status + ".");
        return inquiry;
    }

    public List<TourOperatorRequest> listOperatorRequests() {
        return tourOperatorRequestRepository.findAll().stream()
                .sorted(Comparator.comparing(TourOperatorRequest::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    @Transactional
    public TourOperatorRequest updateOperatorRequest(Long id, TourOperatorRequest.RequestStatus status, BigDecimal partnerPrice,
                                                     String partnerPriceCurrency, String itineraryAssetUrl, String adminNotes,
                                                     String notifyChannel, String actorEmail) {
        TourOperatorRequest request = tourOperatorRequestRepository.findById(id).orElseThrow();
        request.setStatus(status);
        if (partnerPrice != null) {
            request.setPartnerPrice(partnerPrice);
        }
        if (partnerPriceCurrency != null && !partnerPriceCurrency.isBlank()) {
            request.setPartnerPriceCurrency(partnerPriceCurrency.trim().toUpperCase(Locale.ROOT));
        }
        request.setItineraryAssetUrl(itineraryAssetUrl);
        request.setAdminNotes(adminNotes);
        complianceService.audit(actorEmail, "OPS_OPERATOR_REQUEST_UPDATED", "TourOperatorRequest", id, "Status=" + status);
        if (notifyChannel != null && !notifyChannel.isBlank()) {
            request.setLastNotifiedChannel(notifyChannel.trim().toUpperCase(Locale.ROOT));
            request.setLastNotifiedAt(LocalDateTime.now());
        }
        sendNotification(notifyChannel, request.getContactEmail(), request.getContactPhone(),
                "Operator request update",
                "Request #" + request.getId() + " is now " + status + ".");
        return request;
    }

    public List<AvailabilitySlot> listAvailabilitySlots() {
        return availabilitySlotRepository.findAll().stream()
                .sorted(Comparator.comparing(AvailabilitySlot::getSlotDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public List<AvailabilitySlot> listAvailabilitySlots(LocalDate fromDate, LocalDate toDate, Long experienceId) {
        LocalDate from = fromDate == null ? LocalDate.now() : fromDate;
        LocalDate to = toDate == null ? from.plusDays(30) : toDate;
        List<AvailabilitySlot> base = experienceId == null
                ? availabilitySlotRepository.findBySlotDateBetweenOrderBySlotDateAsc(from, to)
                : availabilitySlotRepository.findByExperienceIdAndSlotDateBetweenOrderBySlotDateAsc(experienceId, from, to);
        return base.stream()
                .sorted(Comparator.comparing(AvailabilitySlot::getSlotDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(slot -> slot.getExperience() == null ? Long.MAX_VALUE : slot.getExperience().getId()))
                .toList();
    }

    public List<Experience> listExperiences() {
        return experienceRepository.findAll().stream().sorted(Comparator.comparing(Experience::getTitle)).toList();
    }

    public List<BlackoutDate> listBlackoutDates() {
        return blackoutDateRepository.findAllByOrderByDateValueAsc();
    }

    public List<BlackoutDate> listBlackoutDates(Long experienceId) {
        if (experienceId == null) {
            return listBlackoutDates();
        }
        return blackoutDateRepository.findByExperienceIdOrderByDateValueAsc(experienceId);
    }

    public void generateAvailability(Long experienceId, LocalDate fromDate, LocalDate toDate, Integer capacity) {
        Experience experience = experienceRepository.findById(experienceId).orElseThrow();
        availabilityService.generateRecurringSlots(experience, fromDate, toDate, Math.max(1, capacity == null ? 15 : capacity));
    }

    public void addBlackoutDate(Long experienceId, LocalDate date, String reason) {
        Experience experience = experienceRepository.findById(experienceId).orElseThrow();
        availabilityService.addBlackoutDate(experience, date, reason);
    }

    public void assignGuide(Long slotId, String guideEmail, String guideName) {
        availabilityService.assignGuide(slotId, guideEmail, guideName);
    }

    public void updateSlot(Long slotId, AvailabilitySlot.SlotStatus status, Integer maxCapacity, Integer bookedCount) {
        AvailabilitySlot slot = availabilitySlotRepository.findById(slotId).orElseThrow();
        if (status != null) {
            slot.setStatus(status);
        }
        if (maxCapacity != null) {
            slot.setMaxCapacity(Math.max(0, maxCapacity));
        }
        if (bookedCount != null) {
            slot.setBookedCount(Math.max(0, bookedCount));
        }
        if (slot.getStatus() != AvailabilitySlot.SlotStatus.REQUEST_ONLY) {
            availabilityService.recalculateStatus(slot);
        }
        availabilitySlotRepository.save(slot);
    }

    public void removeBlackout(Long blackoutId) {
        BlackoutDate blackout = blackoutDateRepository.findById(blackoutId).orElseThrow();
        blackoutDateRepository.delete(blackout);
        availabilitySlotRepository.findByExperienceIdAndSlotDate(blackout.getExperience().getId(), blackout.getDateValue())
                .ifPresent(slot -> {
                    if (slot.getStatus() == AvailabilitySlot.SlotStatus.FULLY_BOOKED && slot.getMaxCapacity() == 0) {
                        slot.setMaxCapacity(slot.getExperience().getMaxGroupSize() == null ? 15 : slot.getExperience().getMaxGroupSize());
                        availabilityService.recalculateStatus(slot);
                        availabilitySlotRepository.save(slot);
                    }
                });
    }

    public List<Booking> bookingsForSlot(Long experienceId, LocalDate slotDate) {
        if (experienceId == null || slotDate == null) {
            return List.of();
        }
        return bookingRepository.findByExperienceIdAndPreferredDateAndStatusNot(experienceId, slotDate, Booking.BookingStatus.CANCELLED)
                .stream()
                .sorted(Comparator.comparing(Booking::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    public List<User> listGuideUsers() {
        return userRepository.findByRoles_NameIn(List.of("OPS_MANAGER", "SUPER_ADMIN"))
                .stream()
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u, (a, b) -> a))
                .values().stream()
                .sorted(Comparator.comparing(User::getFullName))
                .toList();
    }

    public List<ClientProfileRow> listClientProfiles() {
        Map<String, ClientProfileAccumulator> map = new LinkedHashMap<>();

        userRepository.findByRoles_NameIn(List.of("REGISTERED_CLIENT")).forEach(user -> {
            String email = normalize(user.getEmail());
            if (email == null) return;
            ClientProfileAccumulator acc = map.computeIfAbsent(email, key -> new ClientProfileAccumulator());
            acc.email = email;
            acc.name = user.getFullName();
            acc.phone = user.getPhone();
            acc.country = user.getCountry();
            acc.registered = true;
        });

        for (Booking booking : bookingRepository.findAll()) {
            String email = normalize(booking.getGuestEmail());
            if (email == null) continue;
            ClientProfileAccumulator acc = map.computeIfAbsent(email, key -> new ClientProfileAccumulator());
            acc.email = email;
            acc.name = firstNonBlank(acc.name, booking.getGuestName());
            acc.phone = firstNonBlank(acc.phone, booking.getGuestPhone());
            acc.country = firstNonBlank(acc.country, booking.getGuestCountry());
            acc.bookingCount++;
        }

        for (ShippingOrder order : shippingOrderRepository.findAll()) {
            String email = normalize(order.getRecipientEmail());
            if (email == null) continue;
            ClientProfileAccumulator acc = map.computeIfAbsent(email, key -> new ClientProfileAccumulator());
            acc.email = email;
            acc.name = firstNonBlank(acc.name, order.getRecipientName());
            acc.phone = firstNonBlank(acc.phone, order.getRecipientPhone());
            acc.country = firstNonBlank(acc.country, order.getCountry());
            acc.orderCount++;
        }

        for (ContactInquiry inquiry : contactInquiryRepository.findAll()) {
            String email = normalize(inquiry.getEmail());
            if (email == null) continue;
            ClientProfileAccumulator acc = map.computeIfAbsent(email, key -> new ClientProfileAccumulator());
            acc.email = email;
            acc.name = firstNonBlank(acc.name, inquiry.getFullName());
            acc.phone = firstNonBlank(acc.phone, inquiry.getPhone());
            acc.inquiryCount++;
        }

        return map.values().stream()
                .map(acc -> new ClientProfileRow(
                        acc.email,
                        acc.name == null ? "-" : acc.name,
                        acc.phone == null ? "-" : acc.phone,
                        acc.country == null ? "-" : acc.country,
                        acc.bookingCount,
                        acc.orderCount,
                        acc.inquiryCount,
                        acc.registered
                ))
                .sorted(Comparator.comparing(ClientProfileRow::email))
                .toList();
    }

    private void sendNotification(String channel, String email, String phone, String subject, String body) {
        String normalizedChannel = normalizeChannel(channel);
        if ("EMAIL".equals(normalizedChannel) && email != null && !email.isBlank()) {
            notificationService.sendEmailAsync(email.trim().toLowerCase(Locale.ROOT), subject, body);
        } else if ("WHATSAPP".equals(normalizedChannel) && phone != null && !phone.isBlank()) {
            notificationService.sendWhatsAppAsync(phone, subject, body);
        } else if ("BOTH".equals(normalizedChannel)) {
            if (email != null && !email.isBlank()) notificationService.sendEmailAsync(email.trim().toLowerCase(Locale.ROOT), subject, body);
            if (phone != null && !phone.isBlank()) notificationService.sendWhatsAppAsync(phone, subject, body);
        }
    }

    private String firstNonBlank(String current, String next) {
        if (current != null && !current.isBlank()) {
            return current;
        }
        return next;
    }

    private String normalizeChannel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static class ClientProfileAccumulator {
        String email;
        String name;
        String phone;
        String country;
        long bookingCount;
        long orderCount;
        long inquiryCount;
        boolean registered;
    }

    public record ClientProfileRow(
            String email,
            String name,
            String phone,
            String country,
            long bookingCount,
            long orderCount,
            long inquiryCount,
            boolean registered
    ) {}
}
