package com.volcanoartscenter.platform.web.internal.superadmin.service;

import com.volcanoartscenter.platform.shared.model.*;
import com.volcanoartscenter.platform.shared.repository.*;
import com.volcanoartscenter.platform.shared.audit.Audited;
import com.volcanoartscenter.platform.shared.service.AvailabilityService;
import com.volcanoartscenter.platform.shared.service.ComplianceService;
import com.volcanoartscenter.platform.shared.service.HtmlSanitizerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private static final Set<String> INTERNAL_STAFF_ROLES = Set.of("SUPER_ADMIN", "CONTENT_MANAGER", "OPS_MANAGER");

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ExperienceRepository experienceRepository;
    private final BookingRepository bookingRepository;
    private final DonationRepository donationRepository;
    private final TalentApplicationRepository talentApplicationRepository;
    private final BlogPostRepository blogPostRepository;
    private final ShippingOrderRepository shippingOrderRepository;
    private final ContactInquiryRepository contactInquiryRepository;
    private final ReviewRepository reviewRepository;
    private final TourOperatorRequestRepository tourOperatorRequestRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final BlackoutDateRepository blackoutDateRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditEventRepository auditEventRepository;
    private final PlatformSettingRepository platformSettingRepository;
    private final AvailabilityService availabilityService;
    private final ComplianceService complianceService;
    private final HtmlSanitizerService htmlSanitizerService;

    public long totalProducts() { return productRepository.count(); }
    public long totalExperiences() { return experienceRepository.count(); }
    public long totalBookings() { return bookingRepository.count(); }
    public long totalDonations() { return donationRepository.count(); }
    public long totalTalentApplications() { return talentApplicationRepository.count(); }
    public long totalBlogPosts() { return blogPostRepository.count(); }
    public long totalShippingOrders() { return shippingOrderRepository.count(); }
    public long totalInquiries() { return contactInquiryRepository.count(); }

    public List<Booking> latestBookings() {
        return bookingRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Booking::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(6)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Product> listProducts() { return productRepository.findAll(); }
    @Transactional(readOnly = true)
    public List<Booking> listBookings() { return bookingRepository.findAll(); }
    @Transactional(readOnly = true)
    public List<Donation> listDonations() { return donationRepository.findAll(); }
    @Transactional(readOnly = true)
    public List<TalentApplication> listTalentApplications() { return talentApplicationRepository.findAll(); }
    @Transactional(readOnly = true)
    public List<BlogPost> listBlogPosts() { return blogPostRepository.findAll(); }
    @Transactional(readOnly = true)
    public List<Review> listReviews() { return reviewRepository.findAll(); }
    @Transactional(readOnly = true)
    public List<ShippingOrder> listShippingOrders() { return shippingOrderRepository.findAll(); }
    @Transactional(readOnly = true)
    public List<ContactInquiry> listContactInquiries() { return contactInquiryRepository.findAll(); }
    @Transactional(readOnly = true)
    public List<TourOperatorRequest> listOperatorRequests() { return tourOperatorRequestRepository.findAll(); }
    @Transactional(readOnly = true)
    public List<AvailabilitySlot> listAvailabilitySlots() { return availabilitySlotRepository.findAll(); }
    public List<AuditEvent> latestAuditEvents() { return auditEventRepository.findTop200ByOrderByCreatedAtDesc(); }
    public List<User> listInternalStaff() {
        return userRepository.findByRoles_NameIn(List.of("SUPER_ADMIN", "CONTENT_MANAGER", "OPS_MANAGER"))
                .stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u, (a, b) -> a))
                .values()
                .stream()
                .sorted(Comparator.comparing(User::getEmail))
                .toList();
    }

    public List<User> listExternalAccounts() {
        return userRepository.findAll().stream()
                .filter(user -> user.getRoles() == null
                        || user.getRoles().stream().map(Role::getName).noneMatch(INTERNAL_STAFF_ROLES::contains))
                .sorted(Comparator.comparing(User::getEmail))
                .toList();
    }

    public List<User> listAllUsers() {
        return userRepository.findAll().stream().sorted(Comparator.comparing(User::getEmail)).toList();
    }
    public List<Role> listAllRoles() {
        return roleRepository.findAll().stream().sorted(Comparator.comparing(Role::getName)).toList();
    }
    public List<PlatformSetting> listSettingsByCategory(String category) {
        return platformSettingRepository.findByCategoryOrderByKeyNameAsc(category);
    }

    public void updateBookingStatus(Long id, Booking.BookingStatus status) {
        Booking booking = bookingRepository.findById(id).orElseThrow();
        booking.setStatus(status);
        if (status == Booking.BookingStatus.CONFIRMED) {
            booking.setConfirmedAt(LocalDateTime.now());
        }
        bookingRepository.save(booking);
        complianceService.audit("admin", "BOOKING_STATUS_UPDATED", "Booking", booking.getId(),
                "Status set to " + status);
    }

    public void updateInquiryStatus(Long id, ContactInquiry.InquiryStatus status) {
        ContactInquiry inquiry = contactInquiryRepository.findById(id).orElseThrow();
        inquiry.setStatus(status);
        contactInquiryRepository.save(inquiry);
        complianceService.audit("admin", "INQUIRY_STATUS_UPDATED", "ContactInquiry", inquiry.getId(),
                "Status set to " + status);
    }

    public void updateOperatorRequestStatus(Long id, TourOperatorRequest.RequestStatus status) {
        TourOperatorRequest request = tourOperatorRequestRepository.findById(id).orElseThrow();
        request.setStatus(status);
        if (status == TourOperatorRequest.RequestStatus.INVOICE_PENDING
                && request.getPartnerPrice() == null
                && request.getEstimatedGroupSize() != null
                && request.getEstimatedGroupSize() > 0) {
            BigDecimal basePrice = new BigDecimal("65.00");
            BigDecimal discountFactor = request.getEstimatedGroupSize() >= 10 ? new BigDecimal("0.90") : BigDecimal.ONE;
            request.setPartnerPrice(basePrice.multiply(discountFactor).setScale(2, java.math.RoundingMode.HALF_UP));
            request.setPartnerPriceCurrency("USD");
        }
        tourOperatorRequestRepository.save(request);
        complianceService.audit("admin", "OPERATOR_REQUEST_STATUS_UPDATED", "TourOperatorRequest", request.getId(),
                "Status set to " + status);
    }

    public Product createProduct(String name, String slug, java.math.BigDecimal price) {
        String normalizedSlug = slug.trim().toLowerCase(Locale.ROOT);
        if (productRepository.existsBySlug(normalizedSlug)) {
            throw new IllegalArgumentException("Product slug already exists");
        }
        Product product = Product.builder()
                .name(name)
                .slug(normalizedSlug)
                .description("New product")
                .shortDescription("New product")
                .price(price)
                .inventoryType(Product.InventoryType.BATCH)
                .stockQuantity(1)
                .available(true)
                .featured(false)
                .build();
        Product saved = productRepository.save(product);
        complianceService.audit("admin", "PRODUCT_CREATED", "Product", saved.getId(),
                "Product created: " + saved.getSlug());
        return saved;
    }

    @Audited(action = "BLOG_POST_CREATED", entityType = "BlogPost")
    public BlogPost createBlogPost(String title, String slug, String excerpt, String content) {
        String normalizedSlug = slug.trim().toLowerCase(Locale.ROOT);
        if (blogPostRepository.existsBySlug(normalizedSlug)) {
            throw new IllegalArgumentException("Blog post slug already exists");
        }
        BlogPost post = BlogPost.builder()
                .title(title)
                .slug(normalizedSlug)
                .excerpt(excerpt)
                .content(htmlSanitizerService.sanitizeBlog(content))
                .category(BlogPost.BlogCategory.UPDATE)
                .published(false)
                .build();
        BlogPost saved = blogPostRepository.save(post);
        complianceService.audit("admin", "BLOG_POST_CREATED", "BlogPost", saved.getId(),
                "Blog post created: " + saved.getSlug());
        return saved;
    }

    @Transactional
    @Audited(action = "PRODUCT_UPDATED", entityType = "Product")
    public Product updateProduct(Long id, String name, BigDecimal price, Boolean available, Boolean featured) {
        Product product = productRepository.findById(id).orElseThrow();
        product.setName(name);
        product.setPrice(price);
        product.setAvailable(Boolean.TRUE.equals(available));
        product.setFeatured(Boolean.TRUE.equals(featured));
        complianceService.audit("admin", "PRODUCT_UPDATED", "Product", product.getId(),
                "Updated name/price/availability/featured");
        return product;
    }

    /**
     * Soft-delete enforcement (PRD §4.1): a product with any existing order_items is NEVER hard-deleted —
     * it is transitioned to ARCHIVED so order history retains the snapshot. Only products with zero
     * referencing orders may be hard-deleted.
     */
    @Transactional
    @Audited(action = "PRODUCT_DELETE_OR_ARCHIVE", entityType = "Product")
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id).orElseThrow();
        long referencingOrderItems = orderItemRepository.countByProduct_Id(id);
        if (referencingOrderItems > 0) {
            product.setArtworkStatus(Product.ArtworkStatus.ARCHIVED);
            product.setAvailable(false);
            productRepository.save(product);
            complianceService.audit("admin", "PRODUCT_ARCHIVED", "Product", id,
                    "Soft-archived (has " + referencingOrderItems + " order_items)");
            return;
        }
        productRepository.delete(product);
        complianceService.audit("admin", "PRODUCT_DELETED", "Product", id, "Product hard-deleted (no order history)");
    }

    @Transactional
    @Audited(action = "BLOG_POST_UPDATED", entityType = "BlogPost")
    public BlogPost updateBlogPost(Long id, String title, String excerpt, String content, BlogPost.BlogCategory category, Boolean published) {
        BlogPost post = blogPostRepository.findById(id).orElseThrow();
        post.setTitle(title);
        post.setExcerpt(excerpt);
        post.setContent(htmlSanitizerService.sanitizeBlog(content));
        post.setCategory(category == null ? BlogPost.BlogCategory.UPDATE : category);
        boolean shouldPublish = Boolean.TRUE.equals(published);
        post.setPublished(shouldPublish);
        post.setPublishedAt(shouldPublish ? (post.getPublishedAt() == null ? LocalDateTime.now() : post.getPublishedAt()) : null);
        complianceService.audit("admin", "BLOG_POST_UPDATED", "BlogPost", post.getId(),
                "Updated content and publication state");
        return post;
    }

    public void deleteBlogPost(Long id) {
        BlogPost post = blogPostRepository.findById(id).orElseThrow();
        blogPostRepository.delete(post);
        complianceService.audit("admin", "BLOG_POST_DELETED", "BlogPost", id, "Blog post deleted");
    }

    public void generateAvailability(Long experienceId, LocalDate from, LocalDate to, Integer capacity) {
        Experience experience = experienceRepository.findById(experienceId).orElseThrow();
        availabilityService.generateRecurringSlots(experience, from, to, Math.max(1, capacity == null ? 15 : capacity));
        complianceService.audit("admin", "AVAILABILITY_GENERATED", "Experience", experienceId,
                "From=" + from + ", to=" + to + ", capacity=" + capacity);
    }

    public void addBlackoutDate(Long experienceId, LocalDate date, String reason) {
        Experience experience = experienceRepository.findById(experienceId).orElseThrow();
        availabilityService.addBlackoutDate(experience, date, reason);
        complianceService.audit("admin", "BLACKOUT_DATE_ADDED", "Experience", experienceId,
                "Date=" + date + ", reason=" + reason);
    }

    public Object listExperiences() {
        return experienceRepository.findAll();
    }

    public Object listBlackoutDates() {
        return blackoutDateRepository.findAll();
    }

    public User createStaffUser(String email, String firstName, String lastName, String encodedPassword, List<String> roleNames, String actorEmail) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("User email already exists");
        }
        List<Role> roles = roleRepository.findByNameIn(roleNames == null ? List.of() : roleNames);
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        User user = User.builder()
                .email(email.trim().toLowerCase(Locale.ROOT))
                .firstName(firstName)
                .lastName(lastName)
                .password(encodedPassword)
                .enabled(true)
                .roles(Set.copyOf(roles))
                .build();
        User saved = userRepository.save(user);
        complianceService.audit(actorEmail, "STAFF_USER_CREATED", "User", saved.getId(),
                "Staff account created for " + saved.getEmail() + " roles=" + roleNames);
        return saved;
    }

    @Transactional
    public User updateStaffUser(Long userId, String firstName, String lastName, String phone, String country, Boolean enabled, String actorEmail) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhone(phone);
        user.setCountry(country);
        user.setEnabled(Boolean.TRUE.equals(enabled));
        complianceService.audit(actorEmail, "STAFF_USER_UPDATED", "User", userId, "Staff profile/status updated");
        return user;
    }

    @Transactional
    public User assignRoles(Long userId, List<String> roleNames, String actorEmail) {
        User user = userRepository.findById(userId).orElseThrow();
        List<Role> roles = roleRepository.findByNameIn(roleNames == null ? List.of() : roleNames);
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("At least one valid role is required");
        }
        user.setRoles(Set.copyOf(roles));
        complianceService.audit(actorEmail, "STAFF_ROLES_UPDATED", "User", userId, "Roles=" + roleNames);
        return user;
    }

    @Transactional
    public void deactivateStaff(Long userId, String actorEmail) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setEnabled(false);
        complianceService.audit(actorEmail, "STAFF_USER_DEACTIVATED", "User", userId, "Account deactivated");
    }

    @Transactional
    public void activateStaff(Long userId, String actorEmail) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setEnabled(true);
        complianceService.audit(actorEmail, "STAFF_USER_ACTIVATED", "User", userId, "Account activated");
    }

    @Transactional
    public PlatformSetting saveSetting(String category, String keyName, String valueData, String description, Boolean masked, String actorEmail) {
        PlatformSetting setting = platformSettingRepository.findByCategoryAndKeyName(category, keyName)
                .orElseGet(() -> PlatformSetting.builder().category(category).keyName(keyName).build());
        setting.setValueData(valueData);
        setting.setDescription(description);
        setting.setMasked(Boolean.TRUE.equals(masked));
        setting.setUpdatedBy(actorEmail);
        PlatformSetting saved = platformSettingRepository.save(setting);
        complianceService.audit(actorEmail, "PLATFORM_SETTING_SAVED", "PlatformSetting", saved.getId(),
                category + ":" + keyName);
        return saved;
    }

    @Transactional
    public Booking overrideBooking(Long bookingId, Booking.BookingStatus status, String adminNotes, String actorEmail) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setStatus(status);
        booking.setAdminNotes(adminNotes);
        if (status == Booking.BookingStatus.CONFIRMED && booking.getConfirmedAt() == null) {
            booking.setConfirmedAt(LocalDateTime.now());
        }
        complianceService.audit(actorEmail, "BOOKING_OVERRIDE", "Booking", bookingId,
                "Status=" + status + ", notes=" + adminNotes);
        return booking;
    }

    @Transactional
    public ShippingOrder updateOrderStatus(Long orderId, ShippingOrder.OrderStatus status, String trackingNumber, String adminNotes, String actorEmail) {
        ShippingOrder order = shippingOrderRepository.findById(orderId).orElseThrow();
        order.setStatus(status);
        order.setTrackingNumber(trackingNumber);
        order.setAdminNotes(adminNotes);
        if (status == ShippingOrder.OrderStatus.SHIPPED || status == ShippingOrder.OrderStatus.IN_TRANSIT) {
            order.setShippedAt(order.getShippedAt() == null ? LocalDateTime.now() : order.getShippedAt());
        }
        if (status == ShippingOrder.OrderStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
        }
        complianceService.audit(actorEmail, "ORDER_STATUS_UPDATED", "ShippingOrder", orderId,
                "Status=" + status + ", tracking=" + trackingNumber);
        return order;
    }

    @Transactional
    public ShippingOrder refundOrder(Long orderId, String disputeReason, String actorEmail) {
        ShippingOrder order = shippingOrderRepository.findById(orderId).orElseThrow();
        order.setPaymentStatus(ShippingOrder.PaymentStatus.REFUNDED);
        order.setStatus(ShippingOrder.OrderStatus.RETURNED);
        order.setAdminNotes(disputeReason);
        complianceService.audit(actorEmail, "ORDER_REFUNDED", "ShippingOrder", orderId,
                "Refund/dispute: " + disputeReason);
        return order;
    }

    @Transactional
    public Donation refundDonation(Long donationId, String disputeReason, String actorEmail) {
        Donation donation = donationRepository.findById(donationId).orElseThrow();
        donation.setStatus(Donation.DonationStatus.REFUNDED);
        complianceService.audit(actorEmail, "DONATION_REFUNDED", "Donation", donationId,
                "Refund/dispute: " + disputeReason);
        return donation;
    }

    public Map<String, Object> exportAllDataSnapshot() {
        return Map.ofEntries(
                Map.entry("users", userRepository.findAll()),
                Map.entry("roles", roleRepository.findAll()),
                Map.entry("products", productRepository.findAll()),
                Map.entry("categories", productCategoryRepository.findAll()),
                Map.entry("experiences", experienceRepository.findAll()),
                Map.entry("bookings", bookingRepository.findAll()),
                Map.entry("shippingOrders", shippingOrderRepository.findAll()),
                Map.entry("donations", donationRepository.findAll()),
                Map.entry("contactInquiries", contactInquiryRepository.findAll()),
                Map.entry("operatorRequests", tourOperatorRequestRepository.findAll()),
                Map.entry("talentApplications", talentApplicationRepository.findAll()),
                Map.entry("reviews", reviewRepository.findAll()),
                Map.entry("blogPosts", blogPostRepository.findAll()),
                Map.entry("auditEvents", auditEventRepository.findTop200ByOrderByCreatedAtDesc()),
                Map.entry("settings", platformSettingRepository.findAll())
        );
    }
}
