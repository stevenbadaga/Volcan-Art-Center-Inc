package com.volcanoartscenter.platform.web.external.registeredclient.service;

import com.volcanoartscenter.platform.shared.model.*;
import com.volcanoartscenter.platform.shared.email.TransactionalEmailService;
import com.volcanoartscenter.platform.shared.notification.NotificationCategory;
import com.volcanoartscenter.platform.shared.notification.NotificationEvent;
import com.volcanoartscenter.platform.shared.notification.StaffNotificationPublisher;
import com.volcanoartscenter.platform.shared.repository.*;
import com.volcanoartscenter.platform.shared.service.AvailabilityService;
import com.volcanoartscenter.platform.shared.service.ComplianceService;
import com.volcanoartscenter.platform.shared.service.NotificationService;
import com.volcanoartscenter.platform.shared.service.integration.IntegrationFacadeService;
import com.volcanoartscenter.platform.shared.service.integration.PaymentGatewayService;
import com.volcanoartscenter.platform.security.clerk.ClerkBackendClient;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegisteredClientService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ReviewRepository reviewRepository;
    private final ExperienceRepository experienceRepository;
    private final BookingRepository bookingRepository;
    private final DonationRepository donationRepository;
    private final DonationCampaignRepository donationCampaignRepository;
    private final TalentApplicationRepository talentApplicationRepository;
    private final BlogPostRepository blogPostRepository;
    private final ShippingOrderRepository shippingOrderRepository;
    private final ContactInquiryRepository contactInquiryRepository;
    private final TourOperatorRequestRepository tourOperatorRequestRepository;
    private final SavedItemRepository savedItemRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final TalentProfileRepository talentProfileRepository;
    private final UserRepository userRepository;
    private final AvailabilityService availabilityService;
    private final ComplianceService complianceService;
    private final IntegrationFacadeService integrationFacadeService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final StaffNotificationPublisher staffNotificationPublisher;
    private final TransactionalEmailService transactionalEmailService;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private ClerkBackendClient clerkBackendClient;

    @Transactional
    public User registerClientAccount(String firstName, String lastName, String email, String phone, String country, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must have at least 8 characters.");
        }
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }
        Role role = roleRepository.findByName("REGISTERED_CLIENT")
                .orElseThrow(() -> new IllegalStateException("REGISTERED_CLIENT role is missing."));
        String clerkUserId = createClerkUser(normalizedEmail, rawPassword, firstName, lastName, "REGISTERED_CLIENT");
        User user = User.builder()
                .email(normalizedEmail)
                .clerkUserId(clerkUserId)
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .country(country)
                .enabled(true)
                .password(passwordEncoder.encode(rawPassword))
                .roles(new HashSet<>(List.of(role)))
                .build();
        User saved = userRepository.save(user);
        complianceService.recordConsent(normalizedEmail, "CLIENT_ACCOUNT_TERMS", true, "client-registration");
        complianceService.audit(normalizedEmail, "CLIENT_ACCOUNT_CREATED", "User", saved.getId(), "Registered client account created.");
        return saved;
    }

    public List<Product> listProducts(String category, String q, BigDecimal minPrice, BigDecimal maxPrice) {
        return productRepository.searchCatalog(category, q, minPrice, maxPrice,
                        Product.ArtworkStatus.PUBLISHED,
                        org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent();
    }

    public org.springframework.data.domain.Page<Product> listProductsPage(String category, String q, BigDecimal minPrice, BigDecimal maxPrice, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, size));
        return productRepository.searchCatalog(category, q, minPrice, maxPrice, Product.ArtworkStatus.PUBLISHED, pageable);
    }

    public List<ProductCategory> activeCategories() {
        return productCategoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc();
    }

    public Optional<Product> findProduct(String slug) {
        return productRepository.findBySlugAndAvailableTrueAndArtworkStatus(slug, Product.ArtworkStatus.PUBLISHED);
    }

    public List<TalentProfile> publishedTalentProfiles() {
        return talentProfileRepository.findByPublishedTrueOrderByIdDesc();
    }

    public List<DonationCampaign> activeDonationCampaigns() {
        return donationCampaignRepository.findByActiveTrueOrderByNameAsc();
    }

    public List<Review> productReviews(Long productId) {
        return reviewRepository.findByProductIdAndApprovedTrueOrderByCreatedAtDesc(productId);
    }

    public boolean canSubmitProductReview(Product product, User user) {
        return user != null
                && shippingOrderRepository.hasDeliveredProduct(user.getId(), product.getId())
                && reviewRepository.findByUserAndProductId(user, product.getId()).isEmpty();
    }

    public String productReviewStatus(Product product, User user) {
        if (user == null) {
            return "Sign in with a registered client account to leave a verified review.";
        }
        if (reviewRepository.findByUserAndProductId(user, product.getId()).isPresent()) {
            return "You already submitted a review for this artwork.";
        }
        if (!shippingOrderRepository.hasDeliveredProduct(user.getId(), product.getId())) {
            return "Review access unlocks after one of your orders for this artwork is delivered.";
        }
        return "Your account is eligible to submit a verified review for this artwork.";
    }

    public void submitProductReview(Product product, User user, String reviewerName, String reviewerEmail, String reviewerCountry,
                                    Integer rating, String comment) {
        int normalized = Math.max(1, Math.min(5, rating));

        // Guard: must be authenticated
        if (user == null) {
            throw new IllegalStateException("You must be logged in to leave a review.");
        }

        // Guard: must have purchased and received this product (DELIVERED status)
        if (!shippingOrderRepository.hasDeliveredProduct(user.getId(), product.getId())) {
            throw new IllegalStateException("You can only review products you have purchased and received.");
        }

        // Guard: one review per user per product
        if (reviewRepository.findByUserAndProductId(user, product.getId()).isPresent()) {
            throw new IllegalStateException("You already reviewed this product.");
        }

        reviewRepository.save(Review.builder()
                .reviewerName(user.getFullName())
                .reviewerEmail(user.getEmail())
                .reviewerCountry(user.getCountry())
                .user(user)
                .rating(normalized)
                .comment(comment)
                .approved(false)
                .featured(false)
                .product(product)
                .build());
    }

    public void submitExperienceReview(Experience experience, User user, String reviewerName, String reviewerEmail, String reviewerCountry,
                                       Integer rating, String comment) {
        int normalized = Math.max(1, Math.min(5, rating));

        // Guard: must be authenticated
        if (user == null) {
            throw new IllegalStateException("You must be logged in to leave a review.");
        }

        // Guard: must have completed this experience (COMPLETED booking)
        if (!bookingRepository.hasCompletedBooking(user.getId(), experience.getId())) {
            throw new IllegalStateException("You can only review experiences you have completed.");
        }

        // Guard: one review per user per experience
        if (reviewRepository.findByUserAndExperienceId(user, experience.getId()).isPresent()) {
            throw new IllegalStateException("You already reviewed this experience.");
        }

        reviewRepository.save(Review.builder()
                .reviewerName(user.getFullName())
                .reviewerEmail(user.getEmail())
                .reviewerCountry(user.getCountry())
                .user(user)
                .rating(normalized)
                .comment(comment)
                .approved(false)
                .featured(false)
                .experience(experience)
                .build());
    }

    public boolean canSubmitExperienceReview(Experience experience, User user) {
        return user != null
                && bookingRepository.hasCompletedBooking(user.getId(), experience.getId())
                && reviewRepository.findByUserAndExperienceId(user, experience.getId()).isEmpty();
    }

    public String experienceReviewStatus(Experience experience, User user) {
        if (user == null) {
            return "Sign in with a registered client account to leave a verified review.";
        }
        if (reviewRepository.findByUserAndExperienceId(user, experience.getId()).isPresent()) {
            return "You already submitted a review for this experience.";
        }
        if (!bookingRepository.hasCompletedBooking(user.getId(), experience.getId())) {
            return "Review access unlocks after one of your bookings for this experience is marked completed.";
        }
        return "Your account is eligible to submit a verified review for this experience.";
    }

    @Transactional
    public ShippingOrder createShippingOrderFromCart(Cart cart, User user, String recipientName, String recipientEmail, String recipientPhone,
                                             String addressLine1, String addressLine2, String city, String state,
                                             String postalCode, String country, String paymentMethod) {
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Your cart is empty.");
        }
        if (recipientName == null || recipientName.isBlank()) {
            throw new IllegalArgumentException("Recipient name is required.");
        }
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new IllegalArgumentException("Recipient email is required.");
        }
        if (addressLine1 == null || addressLine1.isBlank()) {
            throw new IllegalArgumentException("Shipping address is required.");
        }
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("City is required.");
        }
        if (country == null || country.isBlank()) {
            throw new IllegalArgumentException("Country is required.");
        }

        String normalizedRecipientEmail = recipientEmail.trim().toLowerCase(Locale.ROOT);
        String normalizedPaymentMethod = normalizePaymentMethod(paymentMethod);

        BigDecimal productTotal = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        java.util.List<OrderItem> items = new java.util.ArrayList<>();

        ShippingOrder order = ShippingOrder.builder()
                .orderReference("SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT))
                .user(user)
                .recipientName(recipientName.trim())
                .recipientEmail(normalizedRecipientEmail)
                .recipientPhone(recipientPhone)
                .addressLine1(addressLine1.trim())
                .addressLine2(addressLine2)
                .city(city.trim())
                .state(state)
                .postalCode(postalCode)
                .country(country.trim())
                .carrier("Rwanda".equalsIgnoreCase(country) ? ShippingOrder.ShippingCarrier.LOCAL : ShippingOrder.ShippingCarrier.FEDEX)
                .currency("USD")
                .paymentMethod(normalizedPaymentMethod)
                .paymentStatus(ShippingOrder.PaymentStatus.UNPAID)
                .status(ShippingOrder.OrderStatus.PENDING)
                .build();

        // PRD §4.1: server-side re-validation. Never trust cart-supplied values.
        // Re-fetch each product from DB and re-check status, stock, and price freshness.
        for (CartItem ci : cart.getItems()) {
            Product fresh = productRepository.findById(ci.getProduct().getId())
                    .orElseThrow(() -> new IllegalStateException("Product " + ci.getProduct().getId() + " is no longer available."));

            if (fresh.getArtworkStatus() != Product.ArtworkStatus.PUBLISHED || !Boolean.TRUE.equals(fresh.getAvailable())) {
                throw new IllegalStateException("'" + fresh.getName() + "' is no longer available for purchase.");
            }
            int qty = Math.max(1, ci.getQuantity());
            if (fresh.getInventoryType() == Product.InventoryType.BATCH
                    && (fresh.getStockQuantity() == null || fresh.getStockQuantity() < qty)) {
                throw new IllegalStateException("'" + fresh.getName() + "' only has " + fresh.getStockQuantity() + " in stock.");
            }
            BigDecimal unitPrice = fresh.getPrice();
            productTotal = productTotal.add(unitPrice.multiply(BigDecimal.valueOf(qty)));
            totalWeight = totalWeight.add((fresh.getWeightKg() == null ? new BigDecimal("1.0") : fresh.getWeightKg()).multiply(BigDecimal.valueOf(qty)));

            OrderItem item = OrderItem.builder()
               .order(order)
               .product(fresh)
               .quantity(qty)
               .priceAtPurchase(unitPrice)        // priceAtPurchase snapshot (PRD §4.1)
               .productName(fresh.getName())
               .productImageUrl(fresh.getPrimaryImageUrl())
               .build();
            items.add(item);
        }
        
        order.setOrderItems(items);
        order.setProductTotal(productTotal);
        
        // For backwards compatibility until old features fully replaced
        if (!items.isEmpty()) {
            order.setProduct(items.get(0).getProduct());
            order.setQuantity(items.get(0).getQuantity());
        }

        BigDecimal shipping = integrationFacadeService.estimateShipping("Rwanda".equalsIgnoreCase(country) ? "LOCAL" : "FEDEX", country, totalWeight);
        order.setShippingCost(shipping);
        order.setTotalAmount(productTotal.add(shipping));

        ShippingOrder saved = shippingOrderRepository.save(order);
        complianceService.recordConsent(normalizedRecipientEmail, "ORDER_TERMS", true, "cart-checkout-form");
        complianceService.audit(normalizedRecipientEmail, "ORDER_CREATED", "ShippingOrder", saved.getId(),
                "Reference=" + saved.getOrderReference() + ", items=" + items.size() + ", total=" + saved.getTotalAmount());

        if (normalizedPaymentMethod != null) {
            try {
                PaymentGatewayService.PaymentResult payment = integrationFacadeService.initializePayment(
                        normalizedPaymentMethod, saved.getOrderReference(), saved.getTotalAmount(), saved.getCurrency(),
                        java.util.Map.of("email", normalizedRecipientEmail)
                );
                saved.setPaymentTransactionId(payment.externalReference());
                saved.setPaymentStatus(payment.success() ? ShippingOrder.PaymentStatus.PAID : ShippingOrder.PaymentStatus.UNPAID);
            } catch (RuntimeException ex) {
                complianceService.audit(normalizedRecipientEmail, "ORDER_PAYMENT_INIT_FAILED", "ShippingOrder", saved.getId(),
                        normalizedPaymentMethod + ":" + ex.getMessage());
            }
        }

        if (!"Rwanda".equalsIgnoreCase(country)) {
            try {
                saved.setTrackingNumber(integrationFacadeService.createShipment("FEDEX", saved.getOrderReference()));
            } catch (RuntimeException ex) {
                complianceService.audit(normalizedRecipientEmail, "ORDER_SHIPMENT_INIT_FAILED", "ShippingOrder", saved.getId(), ex.getMessage());
            }
        }

        shippingOrderRepository.save(saved);

        // PRD §8: in-app notification on order received.
        if (user != null) {
            eventPublisher.publishEvent(NotificationEvent.forEntity(
                    user.getId(),
                    NotificationCategory.ORDER_RECEIVED,
                    "Order received: " + saved.getOrderReference(),
                    "Your order has been received and is being processed. We'll keep you posted on every status change.",
                    "/client/dashboard",
                    "ShippingOrder",
                    saved.getId()));
        }
        staffNotificationPublisher.notifyAllStaff(
                NotificationCategory.STAFF_NEW_ORDER,
                "New art order: " + saved.getOrderReference(),
                (normalizedRecipientEmail != null ? normalizedRecipientEmail : "Guest") + " placed an order for "
                        + items.size() + " item(s), total $" + saved.getTotalAmount(),
                "/admin/ops/shipping-orders",
                "ShippingOrder",
                saved.getId());
        try {
            notificationService.sendEmailAsync(normalizedRecipientEmail, "Order received: " + saved.getOrderReference(),
                    "Your order has been received and is being processed.");
        } catch (RuntimeException ex) {
            complianceService.audit(normalizedRecipientEmail, "ORDER_EMAIL_FAILED", "ShippingOrder", saved.getId(), ex.getMessage());
        }
        return saved;
    }

    public List<Experience> activeExperiences() {
        return experienceRepository.findByActiveTrueOrderByFeaturedDescTitleAsc();
    }

    public Optional<Experience> findExperience(String slug) {
        return experienceRepository.findBySlugAndActiveTrue(slug);
    }

    public List<Review> experienceReviews(Long experienceId) {
        return reviewRepository.findByExperienceIdAndApprovedTrueOrderByCreatedAtDesc(experienceId);
    }

    public List<AvailabilitySlot> upcomingSlots(Long experienceId) {
        if (experienceId == null) {
            return List.of();
        }
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(30);
        return availabilitySlotRepository.findByExperienceIdAndSlotDateBetweenOrderBySlotDateAsc(experienceId, from, to);
    }

    @Transactional
    public Booking createBooking(Experience experience, User user, String guestName, String guestEmail, String guestPhone, String guestCountry,
                                 LocalDate preferredDate, LocalDate alternativeDate, Integer groupSize, String preferredLanguage,
                                 String paymentMethod, String specialRequests, String tourOperatorName, String tourOperatorEmail) {
        if (experience == null) {
            throw new IllegalArgumentException("Experience is required");
        }
        if (preferredDate == null) {
            throw new IllegalArgumentException("Preferred date is required");
        }
        if (guestName == null || guestName.isBlank()) {
            throw new IllegalArgumentException("Guest name is required");
        }
        if (guestEmail == null || guestEmail.isBlank()) {
            throw new IllegalArgumentException("Guest email is required");
        }
        int safeGroupSize = Math.max(1, groupSize);
        String normalizedGuestEmail = guestEmail.trim().toLowerCase(Locale.ROOT);
        String normalizedPaymentMethod = normalizePaymentMethod(paymentMethod);
        BigDecimal totalPrice = experience.getPricePerPerson() == null
                ? null
                : experience.getPricePerPerson().multiply(BigDecimal.valueOf(safeGroupSize));

        availabilityService.applyBookingToSlot(experience, preferredDate, safeGroupSize);

        Booking booking = Booking.builder()
                .bookingReference("BOOK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT))
                .user(user)
                .guestName(guestName.trim())
                .guestEmail(normalizedGuestEmail)
                .guestPhone(guestPhone)
                .guestCountry(guestCountry)
                .experience(experience)
                .preferredDate(preferredDate)
                .alternativeDate(alternativeDate)
                .groupSize(safeGroupSize)
                .preferredLanguage(preferredLanguage == null || preferredLanguage.isBlank() ? "English" : preferredLanguage)
                .specialRequests(specialRequests)
                .tourOperatorName(tourOperatorName)
                .tourOperatorEmail(tourOperatorEmail)
                .paymentMethod(normalizedPaymentMethod)
                .paymentStatus(Booking.PaymentStatus.UNPAID)
                .status(Booking.BookingStatus.PENDING)
                .totalPrice(totalPrice)
                .build();
        Booking saved = bookingRepository.save(booking);
        complianceService.recordConsent(normalizedGuestEmail, "BOOKING_TERMS", true, "experience-booking-form");
        complianceService.audit(normalizedGuestEmail, "BOOKING_CREATED", "Booking", saved.getId(),
                "Experience=" + experience.getSlug() + ", groupSize=" + safeGroupSize + ", date=" + preferredDate);

        if (normalizedPaymentMethod != null) {
            try {
                PaymentGatewayService.PaymentResult payment = integrationFacadeService.initializePayment(
                        normalizedPaymentMethod, saved.getBookingReference(), saved.getTotalPrice() == null ? BigDecimal.ZERO : saved.getTotalPrice(), "USD",
                        java.util.Map.of("email", normalizedGuestEmail)
                );
                saved.setPaymentStatus(payment.success() ? Booking.PaymentStatus.PAID : Booking.PaymentStatus.UNPAID);
                bookingRepository.save(saved);
            } catch (RuntimeException ex) {
                complianceService.audit(normalizedGuestEmail, "BOOKING_PAYMENT_INIT_FAILED", "Booking", saved.getId(),
                        normalizedPaymentMethod + ":" + ex.getMessage());
            }
        }

        if (user != null) {
            eventPublisher.publishEvent(NotificationEvent.forEntity(
                    user.getId(),
                    NotificationCategory.BOOKING_RECEIVED,
                    "Booking request received: " + saved.getBookingReference(),
                    "Thanks for booking '" + experience.getTitle() + "' on " + preferredDate
                            + ". Our operations team will confirm shortly.",
                    "/client/dashboard",
                    "Booking",
                    saved.getId()));
        }
        try {
            integrationFacadeService.sendEmail(normalizedGuestEmail, "Booking request received: " + saved.getBookingReference(),
                    "Thank you for your booking request. Our operations team will confirm shortly.");
        } catch (RuntimeException ex) {
            complianceService.audit(normalizedGuestEmail, "BOOKING_EMAIL_FAILED", "Booking", saved.getId(), ex.getMessage());
        }
        return saved;
    }

    public Donation createDonation(User user, String donorName, String donorEmail, String donorCountry, BigDecimal amount, String currency,
                                   Donation.DonationPurpose purpose, String message, Boolean isRecurring,
                                   Donation.RecurringFrequency recurringFrequency, String paymentMethod, Long campaignId) {
        if (donorName == null || donorName.isBlank()) {
            throw new IllegalArgumentException("Donor name is required.");
        }
        if (donorEmail == null || donorEmail.isBlank()) {
            throw new IllegalArgumentException("Donor email is required.");
        }
        if (amount == null || amount.compareTo(new BigDecimal("1.00")) < 0) {
            throw new IllegalArgumentException("Donation amount must be at least 1.00.");
        }

        String normalizedPaymentMethod = paymentMethod == null ? "" : paymentMethod.trim().toUpperCase(Locale.ROOT);
        DonationCampaign campaign = campaignId == null ? null : donationCampaignRepository.findById(campaignId).orElse(null);
        Donation donation = Donation.builder()
                .donorName(user == null ? donorName : user.getFullName())
                .donorEmail(user == null ? donorEmail : user.getEmail())
                .donorCountry(user == null ? donorCountry : user.getCountry())
                .user(user)
                .campaign(campaign)
                .amount(amount.max(new BigDecimal("1.00")))
                .currency(currency)
                .purpose(purpose)
                .message(message)
                .isRecurring(Boolean.TRUE.equals(isRecurring))
                .recurringFrequency(Boolean.TRUE.equals(isRecurring) ? recurringFrequency : null)
                .paymentMethod(normalizedPaymentMethod.isBlank() ? "PAY_LATER" : normalizedPaymentMethod)
                .status(Donation.DonationStatus.PENDING)
                .build();
        Donation saved = donationRepository.save(donation);
        complianceService.recordConsent(donorEmail, "DONATION_TERMS", true, "conservation-donation-form");
        complianceService.audit(donorEmail, "DONATION_CREATED", "Donation", saved.getId(),
                "Purpose=" + purpose + ", amount=" + saved.getAmount() + " " + saved.getCurrency()
                        + (campaign != null ? ", campaign=" + campaign.getName() : ""));

        if (!normalizedPaymentMethod.isBlank()) {
            try {
                PaymentGatewayService.PaymentResult payment = integrationFacadeService.initializePayment(
                        normalizedPaymentMethod, "DON-" + saved.getId(), saved.getAmount(), saved.getCurrency(),
                        java.util.Map.of("email", donorEmail)
                );
                saved.setTransactionId(payment.externalReference());
                saved.setStatus(payment.success() ? Donation.DonationStatus.COMPLETED : Donation.DonationStatus.PENDING);
            } catch (RuntimeException ex) {
                complianceService.audit(donorEmail, "DONATION_PAYMENT_INIT_FAILED", "Donation", saved.getId(), ex.getMessage());
            }
        }
        donationRepository.save(saved);

        if (user != null) {
            eventPublisher.publishEvent(NotificationEvent.forEntity(
                    user.getId(),
                    NotificationCategory.DONATION_RECEIVED,
                    "Thank you for your donation",
                    "Your donation of " + saved.getAmount() + " " + saved.getCurrency()
                            + (campaign != null ? " to " + campaign.getName() : "")
                            + " is making a difference. Receipt forthcoming.",
                    "/conservation",
                    "Donation",
                    saved.getId()));
        }
        try {
            integrationFacadeService.sendEmail(donorEmail, "Donation received", "Thank you for supporting Volcano Arts Center.");
        } catch (RuntimeException ex) {
            complianceService.audit(donorEmail, "DONATION_EMAIL_FAILED", "Donation", saved.getId(), ex.getMessage());
        }
        return saved;
    }

    public List<ShippingOrder> ordersForUser(User user) {
        return shippingOrderRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public List<Booking> bookingsForUser(User user) {
        return bookingRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public Optional<Booking> bookingForUser(User user, String bookingReference) {
        if (user == null || bookingReference == null || bookingReference.isBlank()) {
            return Optional.empty();
        }
        return bookingRepository.findByBookingReference(bookingReference.trim())
                .filter(booking -> booking.getUser() != null && booking.getUser().getId().equals(user.getId()));
    }

    public List<Donation> donationsForUser(User user) {
        return donationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public List<Review> reviewsForUser(User user) {
        return reviewRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional
    public void saveProductForUser(User user, Product product) {
        savedItemRepository.findByUserAndProduct(user, product)
                .orElseGet(() -> savedItemRepository.save(SavedItem.builder().user(user).product(product).build()));
    }

    @Transactional
    public void removeSavedProductForUser(User user, Product product) {
        savedItemRepository.deleteByUserAndProduct(user, product);
    }

    public List<SavedItem> savedItemsForUser(User user) {
        return savedItemRepository.findByUserOrderByCreatedAtDesc(user);
    }

    private String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return null;
        }
        return paymentMethod.trim().toUpperCase(Locale.ROOT);
    }

    private String createClerkUser(String email, String rawPassword, String firstName, String lastName, String role) {
        return clerkBackendClient == null ? null : clerkBackendClient.createUser(email, rawPassword, firstName, lastName, role);
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public TalentApplication createTalentApplication(String fullName, String email, String phone, String ageRange, String gender,
                                                     String location, TalentApplication.ApplicantCategory applicantCategory,
                                                     TalentApplication.TalentArea talentArea, String experienceDescription,
                                                     String motivation, String availabilityDetails, String accessibilityNeeds) {
        TalentApplication application = TalentApplication.builder()
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .ageRange(ageRange)
                .gender(gender)
                .location(location)
                .applicantCategory(applicantCategory)
                .talentArea(talentArea)
                .experienceDescription(experienceDescription)
                .motivation(motivation)
                .availabilityDetails(availabilityDetails)
                .accessibilityNeeds(accessibilityNeeds)
                .status(TalentApplication.ApplicationStatus.PENDING)
                .build();
        TalentApplication saved = talentApplicationRepository.save(application);
        complianceService.recordConsent(email, "TALENT_APPLICATION_CONSENT", true, "talent-application-form");
        complianceService.audit(email, "TALENT_APPLICATION_CREATED", "TalentApplication", saved.getId(),
                "Category=" + applicantCategory + ", area=" + talentArea);

        // Notify the applicant in-app (PRD §8) if they have a registered account.
        if (email != null && !email.isBlank()) {
            userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                    .ifPresent(applicantUser -> eventPublisher.publishEvent(NotificationEvent.forEntity(
                            applicantUser.getId(),
                            NotificationCategory.TALENT_APPLICATION_RECEIVED,
                            "Talent application received",
                            "Thank you for applying. Your reference is APP-" + saved.getId()
                                    + ". We'll review and respond within a few business days.",
                            "/talent/dashboard",
                            "TalentApplication",
                            saved.getId())));
        }
        staffNotificationPublisher.notifyAllStaff(
                NotificationCategory.STAFF_NEW_TALENT_APPLICATION,
                "New talent application: " + fullName,
                (talentArea != null ? talentArea.name() : "Talent") + " applicant from "
                        + (location != null ? location : "Rwanda"),
                "/admin/ops/talent-applications",
                "TalentApplication",
                saved.getId());
        transactionalEmailService.sendTalentApplication(saved);
        return saved;
    }

    public List<BlogPost> publishedPosts() {
        return blogPostRepository.findByPublishedTrueOrderByPublishedAtDesc();
    }

    public Optional<BlogPost> viewBlogPost(String slug) {
        Optional<BlogPost> postOpt = blogPostRepository.findBySlugAndPublishedTrue(slug);
        postOpt.ifPresent(post -> {
            post.setViewCount(post.getViewCount() + 1);
            blogPostRepository.save(post);
        });
        return postOpt;
    }

    public void createContactInquiry(String name, String email, String phone, String subject, String message) {
        ContactInquiry inquiry = contactInquiryRepository.save(ContactInquiry.builder()
                .fullName(name)
                .email(email)
                .phone(phone)
                .subject(subject)
                .message(message)
                .status(ContactInquiry.InquiryStatus.NEW)
                .build());
        complianceService.recordConsent(email, "CONTACT_COMMUNICATION", true, "contact-form");
        complianceService.audit(email, "CONTACT_INQUIRY_CREATED", "ContactInquiry", inquiry.getId(),
                "Subject=" + subject);
        staffNotificationPublisher.notifyAllStaff(
                NotificationCategory.STAFF_NEW_INQUIRY,
                "New contact inquiry: " + (subject != null ? subject : "General"),
                (name != null ? name : "Visitor") + ": " + previewMessage(message),
                "/admin/ops/contact-inquiries",
                "ContactInquiry",
                inquiry.getId());
        transactionalEmailService.sendContactInquiry(inquiry);
    }

    private String previewMessage(String message) {
        if (message == null) return "";
        String oneLine = message.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 120 ? oneLine.substring(0, 120) + "…" : oneLine;
    }

    public TourOperatorRequest createTourOperatorRequest(String companyName, String contactName, String contactEmail, String contactPhone,
                                                         String country, String requestedExperienceSlug, Integer estimatedGroupSize,
                                                         LocalDate estimatedDate, Boolean invoiceRequired, String requestDetails) {
        TourOperatorRequest request = TourOperatorRequest.builder()
                .companyName(companyName)
                .contactName(contactName)
                .contactEmail(contactEmail)
                .ownerEmail(contactEmail.trim().toLowerCase(Locale.ROOT))
                .contactPhone(contactPhone)
                .country(country)
                .requestedExperienceSlug(requestedExperienceSlug)
                .estimatedGroupSize(estimatedGroupSize)
                .estimatedDate(estimatedDate)
                .invoiceRequired(invoiceRequired == null || invoiceRequired)
                .requestDetails(requestDetails)
                .partnerPriceCurrency("USD")
                .status(TourOperatorRequest.RequestStatus.SUBMITTED)
                .build();
        TourOperatorRequest saved = tourOperatorRequestRepository.save(request);
        complianceService.recordConsent(contactEmail, "OPERATOR_REQUEST_CONSENT", true, "tour-operator-form");
        complianceService.audit(contactEmail, "OPERATOR_REQUEST_CREATED", "TourOperatorRequest", saved.getId(),
                "Company=" + companyName + ", experienceSlug=" + requestedExperienceSlug);
        integrationFacadeService.sendEmail(contactEmail, "Tour operator request submitted #" + saved.getId(),
                "Your B2B request was received and is now under review.");
        staffNotificationPublisher.notifyAllStaff(
                NotificationCategory.STAFF_NEW_OPERATOR_REQUEST,
                "New tour operator request: " + companyName,
                (contactName != null ? contactName : "Partner") + " requested "
                        + (requestedExperienceSlug != null ? requestedExperienceSlug : "an experience"),
                "/admin/ops/operator-requests",
                "TourOperatorRequest",
                saved.getId());
        return saved;
    }

    public List<TourOperatorRequest> operatorRequestsForOwner(String email) {
        if (email == null || email.isBlank()) {
            return java.util.List.of();
        }
        return tourOperatorRequestRepository.findByOwnerEmailOrderByCreatedAtDesc(email.trim().toLowerCase(Locale.ROOT));
    }

    public Optional<User> findUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT));
    }

    public User saveUserProfile(User user) {
        return userRepository.save(user);
    }
}
