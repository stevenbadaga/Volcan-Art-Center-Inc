package com.volcanoartscenter.platform.web.internal.superadmin.controller;

import com.volcanoartscenter.platform.shared.model.DonationCampaign;
import com.volcanoartscenter.platform.shared.repository.DonationCampaignRepository;
import com.volcanoartscenter.platform.web.internal.superadmin.service.SuperAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class SuperAdminController {

    private static final Set<String> INTERNAL_STAFF_ROLES = Set.of("SUPER_ADMIN", "CONTENT_MANAGER", "OPS_MANAGER");

    @Value("${platform.storage.local-upload-dir:${user.home}/.volcano-platform/uploads}")
    private String localUploadDir;

    private final SuperAdminService superAdminService;
    private final PasswordEncoder passwordEncoder;
    private final DonationCampaignRepository donationCampaignRepository;
    private final com.volcanoartscenter.platform.shared.repository.ReviewRepository reviewRepository;
    private final com.volcanoartscenter.platform.shared.repository.ProductCategoryRepository productCategoryRepository;
    private final com.volcanoartscenter.platform.shared.repository.ProductCollectionRepository productCollectionRepository;

    // ── Dashboard Overview ──
    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        model.addAttribute("pageTitle", "Admin Dashboard");
        model.addAttribute("adminPage", "dashboard");
        model.addAttribute("totalProducts", superAdminService.totalProducts());
        model.addAttribute("totalExperiences", superAdminService.totalExperiences());
        model.addAttribute("totalBookings", superAdminService.totalBookings());
        model.addAttribute("totalDonations", superAdminService.totalDonations());
        model.addAttribute("totalTalentApplications", superAdminService.totalTalentApplications());
        model.addAttribute("totalBlogPosts", superAdminService.totalBlogPosts());
        model.addAttribute("totalShippingOrders", superAdminService.totalShippingOrders());
        model.addAttribute("totalInquiries", superAdminService.totalInquiries());
        model.addAttribute("latestBookings", superAdminService.latestBookings());
        model.addAttribute("auditEvents", superAdminService.latestAuditEvents());
        model.addAttribute("campaigns", donationCampaignRepository.findAll());
        model.addAttribute("pendingReviewsCount", reviewRepository.findAll().stream().filter(r -> !Boolean.TRUE.equals(r.getApproved())).count());
        model.addAttribute("totalCategories", productCategoryRepository.count());
        model.addAttribute("totalCollections", productCollectionRepository.count());
        return "internal/super-admin/dashboard";
    }

    // ── Review Moderation ──
    @GetMapping("/admin/reviews")
    public String reviewsPage(Model model) {
        var reviews = reviewRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(com.volcanoartscenter.platform.shared.model.Review::getCreatedAt, java.util.Comparator.reverseOrder()))
                .toList();
        model.addAttribute("pageTitle", "Review Moderation");
        model.addAttribute("adminPage", "reviews");
        model.addAttribute("reviews", reviews);
        return "internal/super-admin/reviews";
    }

    @PostMapping("/admin/reviews/{id}/approve")
    public String approveReview(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        var review = reviewRepository.findById(id).orElseThrow();
        review.setApproved(true);
        review.setApprovedAt(java.time.LocalDateTime.now());
        reviewRepository.save(review);
        redirectAttributes.addFlashAttribute("successMessage", "Review approved.");
        return "redirect:/admin/reviews";
    }

    @PostMapping("/admin/reviews/{id}/delete")
    public String deleteReview(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        reviewRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Review deleted.");
        return "redirect:/admin/reviews";
    }

    // ── Staff & Accounts ──
    @GetMapping("/admin/users")
    public String usersPage(@RequestParam(defaultValue = "all") String tab, Model model) {
        String normalizedTab = normalizeUsersTab(tab);
        var staffUsers = superAdminService.listInternalStaff();
        var externalUsers = superAdminService.listExternalAccounts();
        var allUsers = superAdminService.listAllUsers();
        model.addAttribute("pageTitle", "Staff & Accounts");
        model.addAttribute("adminPage", "users");
        model.addAttribute("allUsers", allUsers);
        model.addAttribute("staffUsers", staffUsers);
        model.addAttribute("externalUsers", externalUsers);
        model.addAttribute("directoryUsers", switch (normalizedTab) {
            case "staff" -> staffUsers;
            case "external" -> externalUsers;
            default -> allUsers;
        });
        model.addAttribute("activeDirectoryTab", normalizedTab);
        model.addAttribute("totalManagedAccounts", allUsers.size());
        model.addAttribute("allRoles", superAdminService.listAllRoles());
        return "internal/super-admin/users";
    }

    @PostMapping("/admin/users/admins")
    public String createStaff(@RequestParam String email,
                              @RequestParam String firstName,
                              @RequestParam String lastName,
                              @RequestParam String password,
                              @RequestParam(name = "roles", required = false) List<String> roles,
                              @RequestParam(defaultValue = "all") String tab,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            String actor = authentication == null ? "system" : authentication.getName();
            superAdminService.createStaffUser(email, firstName, lastName, passwordEncoder.encode(password), roles, actor);
            redirectAttributes.addFlashAttribute("successMessage",
                    hasInternalStaffRole(roles)
                            ? "Account created."
                            : "Account created with external-only roles. It is now available in External Accounts.");
        } catch (IllegalStateException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/users?tab=" + resolveUsersRedirectTab(tab, hasInternalStaffRole(roles));
    }

    @PostMapping("/admin/users/{id}/update")
    public String updateStaff(@PathVariable Long id,
                              @RequestParam String firstName,
                              @RequestParam String lastName,
                              @RequestParam(required = false) String phone,
                              @RequestParam(required = false) String country,
                              @RequestParam(defaultValue = "false") Boolean enabled,
                              @RequestParam(defaultValue = "all") String tab,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        String actor = authentication == null ? "system" : authentication.getName();
        superAdminService.updateStaffUser(id, firstName, lastName, phone, country, enabled, actor);
        redirectAttributes.addFlashAttribute("successMessage", "Account updated.");
        return "redirect:/admin/users?tab=" + normalizeUsersTab(tab);
    }

    @PostMapping("/admin/users/{id}/roles")
    public String updateStaffRoles(@PathVariable Long id,
                                   @RequestParam(name = "roles") List<String> roles,
                                   @RequestParam(defaultValue = "all") String tab,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            String actor = authentication == null ? "system" : authentication.getName();
            superAdminService.assignRoles(id, roles, actor);
            String normalizedTab = normalizeUsersTab(tab);
            if (hasInternalStaffRole(roles)) {
                redirectAttributes.addFlashAttribute("successMessage", "Roles updated.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                        "external".equals(normalizedTab)
                                ? "Roles updated. This account remains in External Accounts."
                                : "Roles updated. This account moved to External Accounts because it no longer has a staff role.");
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/users?tab=" + resolveUsersRedirectTab(tab, hasInternalStaffRole(roles));
    }

    @PostMapping("/admin/users/{id}/deactivate")
    public String deactivateStaff(@PathVariable Long id,
                                  @RequestParam(defaultValue = "all") String tab,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        String actor = authentication == null ? "system" : authentication.getName();
        superAdminService.deactivateStaff(id, actor);
        redirectAttributes.addFlashAttribute("successMessage", "Account deactivated.");
        return "redirect:/admin/users?tab=" + normalizeUsersTab(tab);
    }

    @PostMapping("/admin/users/{id}/activate")
    public String activateStaff(@PathVariable Long id,
                                  @RequestParam(defaultValue = "all") String tab,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        String actor = authentication == null ? "system" : authentication.getName();
        superAdminService.activateStaff(id, actor);
        redirectAttributes.addFlashAttribute("successMessage", "Account activated.");
        return "redirect:/admin/users?tab=" + normalizeUsersTab(tab);
    }

    // ── Platform Settings ──
    @GetMapping("/admin/settings")
    public String settingsPage(Model model) {
        model.addAttribute("pageTitle", "Platform Settings");
        model.addAttribute("adminPage", "settings");
        model.addAttribute("paymentSettings", superAdminService.listSettingsByCategory("PAYMENT"));
        model.addAttribute("integrationSettings", superAdminService.listSettingsByCategory("INTEGRATION"));
        model.addAttribute("platformSettings", superAdminService.listSettingsByCategory("PLATFORM"));
        return "internal/super-admin/settings";
    }

    @PostMapping("/admin/settings")
    public String saveSetting(@RequestParam String category,
                              @RequestParam String keyName,
                              @RequestParam(required = false) String valueData,
                              @RequestParam(required = false) String description,
                              @RequestParam(defaultValue = "false") Boolean masked,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        String actor = authentication == null ? "system" : authentication.getName();
        superAdminService.saveSetting(category, keyName, valueData, description, masked, actor);
        redirectAttributes.addFlashAttribute("successMessage", "Setting saved.");
        return "redirect:/admin/settings";
    }

    // ── Audit Log ──
    @GetMapping("/admin/audit-log")
    public String auditLogPage(Model model) {
        model.addAttribute("pageTitle", "Audit Log");
        model.addAttribute("adminPage", "audit-log");
        model.addAttribute("auditEvents", superAdminService.latestAuditEvents());
        return "internal/super-admin/audit-log";
    }

    // ── Overrides & Refunds ──
    @GetMapping("/admin/overrides")
    public String overridesPage(Model model) {
        model.addAttribute("pageTitle", "Overrides & Refunds");
        model.addAttribute("adminPage", "overrides");
        model.addAttribute("bookings", superAdminService.listBookings());
        model.addAttribute("orders", superAdminService.listShippingOrders());
        model.addAttribute("donations", superAdminService.listDonations());
        return "internal/super-admin/overrides";
    }

    @PostMapping("/admin/bookings/{id}/override")
    public String overrideBooking(@PathVariable Long id,
                                  @RequestParam com.volcanoartscenter.platform.shared.model.Booking.BookingStatus status,
                                  @RequestParam(required = false) String adminNotes,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        String actor = authentication == null ? "system" : authentication.getName();
        superAdminService.overrideBooking(id, status, adminNotes, actor);
        redirectAttributes.addFlashAttribute("successMessage", "Booking overridden.");
        return "redirect:/admin/overrides";
    }

    @PostMapping("/admin/orders/{id}/status")
    public String updateOrderStatus(@PathVariable Long id,
                                    @RequestParam com.volcanoartscenter.platform.shared.model.ShippingOrder.OrderStatus status,
                                    @RequestParam(required = false) String trackingNumber,
                                    @RequestParam(required = false) String adminNotes,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        String actor = authentication == null ? "system" : authentication.getName();
        superAdminService.updateOrderStatus(id, status, trackingNumber, adminNotes, actor);
        redirectAttributes.addFlashAttribute("successMessage", "Order status updated.");
        return "redirect:/admin/overrides";
    }

    @PostMapping("/admin/orders/{id}/refund")
    public String refundOrder(@PathVariable Long id,
                              @RequestParam String disputeReason,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        String actor = authentication == null ? "system" : authentication.getName();
        superAdminService.refundOrder(id, disputeReason, actor);
        redirectAttributes.addFlashAttribute("successMessage", "Order refunded and dispute recorded.");
        return "redirect:/admin/overrides";
    }

    @PostMapping("/admin/donations/{id}/refund")
    public String refundDonation(@PathVariable Long id,
                                 @RequestParam String disputeReason,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        String actor = authentication == null ? "system" : authentication.getName();
        superAdminService.refundDonation(id, disputeReason, actor);
        redirectAttributes.addFlashAttribute("successMessage", "Donation refunded and dispute recorded.");
        return "redirect:/admin/overrides";
    }

    @GetMapping(value = "/admin/export", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> exportAllData() {
        return ResponseEntity.ok(superAdminService.exportAllDataSnapshot());
    }

    // ── Conservation Campaigns ──
    @GetMapping("/admin/conservation-campaigns")
    public String campaignsPage(Model model) {
        model.addAttribute("pageTitle", "Conservation Campaigns");
        model.addAttribute("adminPage", "campaigns");
        model.addAttribute("campaigns", donationCampaignRepository.findAll());
        return "internal/super-admin/campaigns";
    }

    @PostMapping("/admin/conservation-campaigns")
    public String createCampaign(@RequestParam String name,
                                 @RequestParam(required = false) String description,
                                 @RequestParam(required = false) String impactStatement,
                                 @RequestParam(required = false) BigDecimal goalAmount,
                                 @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                 @RequestParam(required = false) String imageUrl,
                                 @RequestParam(defaultValue = "true") Boolean active,
                                 RedirectAttributes redirectAttributes) {
        String finalImageUrl = (imageUrl != null && !imageUrl.isBlank()) ? imageUrl : null;
        try {
            if (imageFile != null && !imageFile.isEmpty()) {
                finalImageUrl = handleFileUpload(imageFile);
            }
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("successMessage", "Image upload failed: " + e.getMessage());
            return "redirect:/admin/conservation-campaigns";
        }

        DonationCampaign c = DonationCampaign.builder()
                .name(name)
                .description(description)
                .impactStatement(impactStatement)
                .goalAmount(goalAmount)
                .imageUrl(finalImageUrl)
                .active(active)
                .build();
        donationCampaignRepository.save(c);
        redirectAttributes.addFlashAttribute("successMessage", "Campaign '" + name + "' created.");
        return "redirect:/admin/conservation-campaigns";
    }

    @PostMapping("/admin/conservation-campaigns/{id}/update")
    public String updateCampaign(@PathVariable Long id,
                                 @RequestParam String name,
                                 @RequestParam(required = false) String description,
                                 @RequestParam(required = false) String impactStatement,
                                 @RequestParam(required = false) BigDecimal goalAmount,
                                 @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                 @RequestParam(required = false) String imageUrl,
                                 @RequestParam(defaultValue = "true") Boolean active,
                                 RedirectAttributes redirectAttributes) {
        DonationCampaign c = donationCampaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found"));

        String finalImageUrl = (imageUrl != null && !imageUrl.isBlank()) ? imageUrl : c.getImageUrl();
        try {
            if (imageFile != null && !imageFile.isEmpty()) {
                finalImageUrl = handleFileUpload(imageFile);
            }
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("successMessage", "Image upload failed: " + e.getMessage());
            return "redirect:/admin/conservation-campaigns";
        }

        c.setName(name);
        c.setDescription(description);
        c.setImpactStatement(impactStatement);
        c.setGoalAmount(goalAmount);
        c.setImageUrl(finalImageUrl);
        c.setActive(active);
        donationCampaignRepository.save(c);
        redirectAttributes.addFlashAttribute("successMessage", "Campaign updated.");
        return "redirect:/admin/conservation-campaigns";
    }

    @PostMapping("/admin/conservation-campaigns/{id}/delete")
    public String deleteCampaign(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        donationCampaignRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Campaign deleted.");
        return "redirect:/admin/conservation-campaigns";
    }

    private boolean hasInternalStaffRole(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.stream().anyMatch(INTERNAL_STAFF_ROLES::contains);
    }

    private String normalizeUsersTab(String tab) {
        if ("staff".equalsIgnoreCase(tab)) {
            return "staff";
        }
        if ("external".equalsIgnoreCase(tab)) {
            return "external";
        }
        return "all";
    }

    private String resolveUsersRedirectTab(String requestedTab, boolean hasInternalStaffRole) {
        String normalizedTab = normalizeUsersTab(requestedTab);
        if ("all".equals(normalizedTab)) {
            return "all";
        }
        return hasInternalStaffRole ? "staff" : "external";
    }

    private String handleFileUpload(MultipartFile file) throws IOException {
        Path uploadRoot = Path.of(localUploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot);
        String ext = "";
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot).toLowerCase(Locale.ROOT);
        }
        String storageKey = UUID.randomUUID() + ext;
        Path target = uploadRoot.resolve(storageKey).normalize();
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return "/uploads/" + storageKey;
    }
}
