package com.volcanoartscenter.platform.web.external.registeredclient.controller;

import com.volcanoartscenter.platform.shared.exception.PlatformException;
import com.volcanoartscenter.platform.shared.messaging.Conversation;
import com.volcanoartscenter.platform.shared.messaging.MessagingService;
import com.volcanoartscenter.platform.shared.model.*;
import com.volcanoartscenter.platform.shared.reservation.ProductReservationService;
import com.volcanoartscenter.platform.shared.service.CaptchaService;
import com.volcanoartscenter.platform.web.external.registeredclient.service.RegisteredClientService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class RegisteredClientController {

    private final RegisteredClientService registeredClientService;
    private final CaptchaService captchaService;
    private final ProductReservationService productReservationService;
    private final MessagingService messagingService;

    @Value("${platform.integrations.clerk.publishable-key:}")
    private String clerkPublishableKey;

    @GetMapping("/art-store")
    public String artStore(@RequestParam(required = false) String category,
                           @RequestParam(required = false) String q,
                           @RequestParam(required = false) BigDecimal minPrice,
                           @RequestParam(required = false) BigDecimal maxPrice,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "24") int size,
                           Model model) {
        var productsPage = registeredClientService.listProductsPage(category, q, minPrice, maxPrice, page, size);
        model.addAttribute("currentPage", "art-store");
        model.addAttribute("pageTitle", "Art Store — Volcano Arts Center");
        model.addAttribute("productsPage", productsPage);
        model.addAttribute("products", productsPage.getContent());
        model.addAttribute("categories", registeredClientService.activeCategories());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("q", q);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        return "external/registered-client/art-store";
    }

    @GetMapping("/art-store/paged")
    public String artStorePaged(@RequestParam(required = false) String category,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false) BigDecimal minPrice,
                                @RequestParam(required = false) BigDecimal maxPrice,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "12") int size,
                                Model model) {
        model.addAttribute("currentPage", "art-store");
        model.addAttribute("pageTitle", "Art Store — Volcano Arts Center");
        model.addAttribute("productsPage", registeredClientService.listProductsPage(category, q, minPrice, maxPrice, page, size));
        model.addAttribute("categories", registeredClientService.activeCategories());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("q", q);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        return "external/registered-client/art-store-paged";
    }

    @GetMapping("/art-store/{slug}")
    public String productDetail(@PathVariable String slug,
                                Authentication authentication,
                                HttpSession session,
                                Model model) {
        Product product = registeredClientService.findProduct(slug).orElse(null);
        if (product == null) {
            return "redirect:/art-store";
        }

        User user = currentUser(authentication).orElse(null);
        boolean isAuthenticated = isAuthenticated(authentication);

        // Reservation status for the stock indicator (PRD §4.1).
        boolean isUnique = product.getInventoryType() == Product.InventoryType.UNIQUE;
        boolean isSold = product.getArtworkStatus() == Product.ArtworkStatus.SOLD;
        Long remainingSeconds = isUnique
                ? productReservationService.remainingSeconds(product.getId()).orElse(null)
                : null;
        String myHolderKey = user != null ? "u:" + user.getId() : "s:" + session.getId();
        boolean reservedByOther = isUnique && remainingSeconds != null
                && productReservationService.currentHolder(product.getId())
                    .map(holder -> !holder.equals(myHolderKey))
                    .orElse(false);
        boolean reservedByMe = isUnique && remainingSeconds != null && !reservedByOther;
        Integer remainingMinutes = remainingSeconds == null ? null : (int) Math.max(1, (remainingSeconds + 59) / 60);

        var productReviews = registeredClientService.productReviews(product.getId());
        double averageRating = productReviews.stream()
                .filter(r -> r.getRating() != null)
                .mapToInt(Review::getRating)
                .average().orElse(0d);

        model.addAttribute("currentPage", "art-store");
        model.addAttribute("pageTitle", product.getName() + " — Volcano Arts Center");
        model.addAttribute("product", product);
        model.addAttribute("reviews", productReviews);
        model.addAttribute("reviewCount", productReviews.size());
        model.addAttribute("averageRating", averageRating);
        model.addAttribute("relatedProducts", registeredClientService.listProducts(null, null, null, null).stream()
                .filter(p -> !p.getId().equals(product.getId()))
                .limit(4).toList());
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("isSold", isSold);
        model.addAttribute("isUnique", isUnique);
        model.addAttribute("reservedByOther", reservedByOther);
        model.addAttribute("reservedByMe", reservedByMe);
        model.addAttribute("reservationRemainingMinutes", remainingMinutes);
        model.addAttribute("canSubmitReview", registeredClientService.canSubmitProductReview(product, user));
        model.addAttribute("reviewStatusMessage", registeredClientService.productReviewStatus(product, user));
        model.addAttribute("reviewerDisplayName", user != null ? user.getFullName() : null);
        model.addAttribute("reviewerDisplayEmail", user != null ? user.getEmail() : null);
        return "external/registered-client/product-detail";
    }

    /**
     * "Ask about this artwork" CTA on the product detail page.
     * Authenticated clients open a real Conversation; guests fall back to the
     * legacy ContactInquiry flow (no inbox to read replies from).
     */
    @PostMapping("/art-store/{slug}/ask")
    public String askAboutProduct(@PathVariable String slug,
                                  @RequestParam(required = false) String name,
                                  @RequestParam(required = false) String email,
                                  @RequestParam(required = false) String phone,
                                  @RequestParam String message,
                                  @RequestParam(required = false) String captchaToken,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        if (!captchaService.verify(captchaToken)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Captcha validation failed.");
            return "redirect:/art-store/" + slug;
        }
        Product product = registeredClientService.findProduct(slug).orElse(null);
        if (product == null) {
            return "redirect:/art-store";
        }
        User user = currentUser(authentication).orElse(null);
        if (user != null) {
            try {
                Conversation conv = messagingService.openConversation(
                        user, product.getId(), "Question about: " + product.getName(), message);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Your message has been sent. Replies will appear in your inbox.");
                return "redirect:/account/messages/" + conv.getId();
            } catch (PlatformException ex) {
                redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
                return "redirect:/art-store/" + slug;
            }
        }
        String subject = "Question about: " + product.getName();
        String fullMessage = (message == null ? "" : message)
                + "\n\n— Inquiring about artwork: " + product.getName()
                + " (slug: " + product.getSlug() + ")";
        registeredClientService.createContactInquiry(name, email, phone, subject, fullMessage);
        redirectAttributes.addFlashAttribute("successMessage",
                "Thanks — your question has been sent. Our team will get back to you within 24 hours.");
        return "redirect:/art-store/" + slug;
    }

    @PostMapping("/art-store/{slug}/inquire")
    public String quickInquireAboutProduct(@PathVariable String slug,
                                           Authentication authentication,
                                           RedirectAttributes redirectAttributes) {
        Product product = registeredClientService.findProduct(slug).orElse(null);
        if (product == null) {
            return "redirect:/art-store";
        }

        User user = currentUser(authentication).orElse(null);
        if (user == null) {
            return "redirect:/login?redirect=/art-store/" + slug;
        }

        try {
            String subject = "Inquiry about: " + product.getName();
            String message = "Hello Volcano Arts team, I would like to inquire about \""
                    + product.getName() + "\".";
            Conversation conv = messagingService.openConversation(user, product.getId(), subject, message);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Inquiry started. Continue in your inbox conversation thread.");
            return "redirect:/account/messages/" + conv.getId();
        } catch (PlatformException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/art-store/" + slug;
        }
    }

    @PostMapping("/art-store/{slug}/reviews")
    public String submitProductReview(@PathVariable String slug,
                                      @RequestParam(required = false) String reviewerName,
                                      @RequestParam(required = false) String reviewerEmail,
                                      @RequestParam(required = false) String reviewerCountry,
                                      @RequestParam Integer rating,
                                      @RequestParam String comment,
                                      @RequestParam(required = false) String captchaToken,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {
        if (!captchaService.verify(captchaToken)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Captcha validation failed.");
            return "redirect:/art-store/" + slug;
        }
        Product product = registeredClientService.findProduct(slug).orElse(null);
        if (product == null) {
            return "redirect:/art-store";
        }

        User user = currentUser(authentication).orElse(null);
        try {
            registeredClientService.submitProductReview(product, user, reviewerName, reviewerEmail, reviewerCountry, rating, comment);
            redirectAttributes.addFlashAttribute("successMessage", "Review submitted. It will appear after moderation.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/art-store/" + slug;
    }



    @GetMapping("/experiences")
    public String experiences(Model model) {
        model.addAttribute("currentPage", "experiences");
        model.addAttribute("pageTitle", "Experiences — Volcano Arts Center");
        model.addAttribute("experiences", registeredClientService.activeExperiences());
        return "external/registered-client/experiences";
    }

    @GetMapping("/experiences/{slug}")
    public String experienceDetail(@PathVariable String slug, Authentication authentication, Model model) {
        Experience experience = registeredClientService.findExperience(slug).orElse(null);
        if (experience == null) {
            return "redirect:/experiences";
        }

        User user = currentUser(authentication).orElse(null);
        boolean isAuthenticated = isAuthenticated(authentication);
        var reviews = registeredClientService.experienceReviews(experience.getId());
        double averageRating = reviews.stream()
                .filter(r -> r.getRating() != null)
                .mapToInt(Review::getRating)
                .average().orElse(0d);

        model.addAttribute("currentPage", "experiences");
        model.addAttribute("pageTitle", experience.getTitle() + " — Volcano Arts Center");
        model.addAttribute("experience", experience);
        model.addAttribute("reviews", reviews);
        model.addAttribute("reviewCount", reviews.size());
        model.addAttribute("averageRating", averageRating);
        model.addAttribute("includedItems", splitToList(experience.getWhatsIncluded()));
        model.addAttribute("bringItems", splitToList(experience.getWhatToBring()));
        model.addAttribute("relatedExperiences", registeredClientService.activeExperiences().stream()
                .filter(e -> !e.getId().equals(experience.getId()))
                .limit(3).toList());
        model.addAttribute("availabilitySlots", registeredClientService.upcomingSlots(experience.getId()));
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("canSubmitReview", registeredClientService.canSubmitExperienceReview(experience, user));
        model.addAttribute("reviewStatusMessage", registeredClientService.experienceReviewStatus(experience, user));
        model.addAttribute("reviewerDisplayName", user != null ? user.getFullName() : null);
        model.addAttribute("reviewerDisplayEmail", user != null ? user.getEmail() : null);
        return "external/registered-client/experience-detail";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword(Model model) {
        model.addAttribute("currentPage", "login");
        model.addAttribute("pageTitle", "Reset Password — Volcano Arts Center");
        model.addAttribute("clerkPublishableKey", clerkPublishableKey);
        return "external/guest/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String submitForgotPassword() {
        return "redirect:/forgot-password";
    }

    /** Splits an admin-entered block (newlines, commas or semicolons) into clean list items. */
    private List<String> splitToList(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("\\r?\\n|;|,"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @PostMapping("/experiences/{slug}/book")
    public String bookExperience(@PathVariable String slug,
                                 @RequestParam String guestName,
                                 @RequestParam String guestEmail,
                                 @RequestParam(required = false) String guestPhone,
                                 @RequestParam(required = false) String guestCountry,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate preferredDate,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate alternativeDate,
                                 @RequestParam(defaultValue = "1") Integer groupSize,
                                 @RequestParam(required = false) String preferredLanguage,
                                 @RequestParam(required = false) String paymentMethod,
                                 @RequestParam(required = false) String specialRequests,
                                 @RequestParam(required = false) String tourOperatorName,
                                 @RequestParam(required = false) String tourOperatorEmail,
                                 @RequestParam(required = false) String captchaToken,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(authentication)) {
            redirectAttributes.addFlashAttribute("successMessage", "Please register or sign in to book experiences.");
            return "redirect:/login";
        }
        if (!captchaService.verify(captchaToken)) {
            redirectAttributes.addFlashAttribute("successMessage", "Captcha validation failed.");
            return "redirect:/experiences/" + slug;
        }
        Experience experience = registeredClientService.findExperience(slug).orElse(null);
        if (experience == null) {
            return "redirect:/experiences";
        }
        User user = currentUser(authentication).orElse(null);

        try {
            Booking booking = registeredClientService.createBooking(
                    experience,
                    user,
                    user == null ? guestName : user.getFullName(),
                    user == null ? guestEmail : user.getEmail(),
                    user == null ? guestPhone : user.getPhone(),
                    user == null ? guestCountry : user.getCountry(),
                    preferredDate, alternativeDate, groupSize, preferredLanguage,
                    paymentMethod, specialRequests, tourOperatorName, tourOperatorEmail
            );
            redirectAttributes.addFlashAttribute("successMessage",
                    "Booking request received. Reference: " + booking.getBookingReference() + ".");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/experiences/" + slug;
    }

    @PostMapping("/experiences/{slug}/reviews")
    public String submitExperienceReview(@PathVariable String slug,
                                         @RequestParam(required = false) String reviewerName,
                                         @RequestParam(required = false) String reviewerEmail,
                                         @RequestParam(required = false) String reviewerCountry,
                                         @RequestParam Integer rating,
                                         @RequestParam String comment,
                                         @RequestParam(required = false) String captchaToken,
                                         Authentication authentication,
                                         RedirectAttributes redirectAttributes) {
        if (!captchaService.verify(captchaToken)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Captcha validation failed.");
            return "redirect:/experiences/" + slug;
        }
        Experience experience = registeredClientService.findExperience(slug).orElse(null);
        if (experience == null) {
            return "redirect:/experiences";
        }
        User user = currentUser(authentication).orElse(null);
        try {
            registeredClientService.submitExperienceReview(experience, user, reviewerName, reviewerEmail, reviewerCountry, rating, comment);
            redirectAttributes.addFlashAttribute("successMessage", "Review submitted. It will appear after moderation.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/experiences/" + slug;
    }

    @GetMapping("/conservation")
    public String conservation(Model model) {
        model.addAttribute("currentPage", "conservation");
        model.addAttribute("campaigns", registeredClientService.activeDonationCampaigns());
        model.addAttribute("donationPurposes", Donation.DonationPurpose.values());
        model.addAttribute("recurringFrequencies", Donation.RecurringFrequency.values());
        model.addAttribute("pageTitle", "Conservation — Volcano Arts Center");
        return "external/registered-client/conservation";
    }

    @PostMapping("/conservation/donate")
    public String donate(@RequestParam String donorName,
                         @RequestParam String donorEmail,
                         @RequestParam(required = false) String donorCountry,
                         @RequestParam BigDecimal amount,
                         @RequestParam(defaultValue = "USD") String currency,
                         @RequestParam(defaultValue = "GENERAL") Donation.DonationPurpose purpose,
                         @RequestParam(required = false) String message,
                         @RequestParam(defaultValue = "false") Boolean isRecurring,
                         @RequestParam(required = false) Donation.RecurringFrequency recurringFrequency,
                         @RequestParam(required = false) String paymentMethod,
                         @RequestParam(required = false) Long campaignId,
                         @RequestParam(required = false) String captchaToken,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        if (!captchaService.verify(captchaToken)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Captcha validation failed.");
            return "redirect:/conservation";
        }
        User user = currentUser(authentication).orElse(null);
        try {
            Donation donation = registeredClientService.createDonation(
                    user, donorName, donorEmail, donorCountry, amount, currency, purpose, message, isRecurring, recurringFrequency, paymentMethod, campaignId
            );
            redirectAttributes.addFlashAttribute("successMessage",
                    "Thank you for your support. Donation request #" + donation.getId() + " has been recorded.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/conservation";
    }

    @PostMapping("/art-store/{slug}/save")
    public String saveProduct(@PathVariable String slug, Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("successMessage", "Please sign in as a registered client to save items.");
            return "redirect:/login";
        }
        Product product = registeredClientService.findProduct(slug).orElse(null);
        if (product == null) {
            return "redirect:/art-store";
        }
        registeredClientService.saveProductForUser(user, product);
        redirectAttributes.addFlashAttribute("successMessage", "Item saved to your profile.");
        return "redirect:/art-store/" + slug;
    }

    @PostMapping("/art-store/{slug}/unsave")
    public String unsaveProduct(@PathVariable String slug, Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("successMessage", "Please sign in as a registered client to manage saved items.");
            return "redirect:/login";
        }
        Product product = registeredClientService.findProduct(slug).orElse(null);
        if (product == null) {
            return "redirect:/art-store";
        }
        registeredClientService.removeSavedProductForUser(user, product);
        redirectAttributes.addFlashAttribute("successMessage", "Item removed from saved list.");
        return "redirect:/art-store/" + slug;
    }

    @GetMapping("/talent")
    public String talent(Authentication authentication, Model model) {
        model.addAttribute("currentPage", "talent");
        model.addAttribute("pageTitle", "Talent Program — Volcano Arts Center");
        model.addAttribute("categories", TalentApplication.ApplicantCategory.values());
        model.addAttribute("areas", TalentApplication.TalentArea.values());
        model.addAttribute("profiles", registeredClientService.publishedTalentProfiles());
        model.addAttribute("isAuthenticated", isAuthenticated(authentication));
        model.addAttribute("isTalentApplicant", hasAuthority(authentication, "ROLE_TALENT_APPLICANT"));
        return "external/talent-applicant/talent";
    }

    @PostMapping("/talent/apply")
    public String applyTalent(@RequestParam String fullName,
                              @RequestParam(required = false) String email,
                              @RequestParam(required = false) String phone,
                              @RequestParam(required = false) String ageRange,
                              @RequestParam(required = false) String gender,
                              @RequestParam(required = false) String location,
                              @RequestParam TalentApplication.ApplicantCategory applicantCategory,
                              @RequestParam TalentApplication.TalentArea talentArea,
                              @RequestParam(required = false) String experienceDescription,
                              @RequestParam(required = false) String motivation,
                              @RequestParam(required = false) String availabilityDetails,
                              @RequestParam(required = false) String accessibilityNeeds,
                              @RequestParam(required = false) String captchaToken,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        if (!isAuthenticated(authentication)) {
            redirectAttributes.addFlashAttribute("successMessage", "Please register a talent applicant account and sign in before applying.");
            return "redirect:/talent/register";
        }
        if (!hasAuthority(authentication, "ROLE_TALENT_APPLICANT")) {
            redirectAttributes.addFlashAttribute("successMessage", "Please use a Talent Applicant account to submit this form.");
            return "redirect:/talent/register";
        }
        if (!captchaService.verify(captchaToken)) {
            redirectAttributes.addFlashAttribute("successMessage", "Captcha validation failed.");
            return "redirect:/talent";
        }
        registeredClientService.createTalentApplication(fullName, email, phone, ageRange, gender, location, applicantCategory, talentArea, experienceDescription, motivation, availabilityDetails, accessibilityNeeds);
        redirectAttributes.addFlashAttribute("successMessage",
                "Application submitted successfully. We will contact you after review.");
        return "redirect:/talent";
    }

    @GetMapping("/blog")
    public String blog(Model model) {
        model.addAttribute("currentPage", "blog");
        model.addAttribute("pageTitle", "Blog & News — Volcano Arts Center");
        model.addAttribute("posts", registeredClientService.publishedPosts());
        model.addAttribute("leadExperiences", registeredClientService.activeExperiences().stream().limit(3).toList());
        return "external/guest/blog";
    }

    @GetMapping("/blog/{slug}")
    public String blogDetail(@PathVariable String slug, Model model) {
        BlogPost post = registeredClientService.viewBlogPost(slug).orElse(null);
        if (post == null) {
            return "redirect:/blog";
        }
        var relatedPosts = registeredClientService.publishedPosts()
                .stream()
                .filter(p -> p.getSlug() != null && !p.getSlug().equals(post.getSlug()))
                .limit(3)
                .toList();
        model.addAttribute("currentPage", "blog");
        model.addAttribute("pageTitle", post.getTitle() + " — Volcano Arts Center");
        model.addAttribute("post", post);
        model.addAttribute("relatedPosts", relatedPosts);
        return "external/guest/blog-detail";
    }

    @PostMapping("/contact")
    public String submitInquiry(@RequestParam String name,
                                @RequestParam String email,
                                @RequestParam(required = false) String phone,
                                @RequestParam String subject,
                                @RequestParam String message,
                                @RequestParam(required = false) String captchaToken,
                                RedirectAttributes redirectAttributes) {
        if (!captchaService.verify(captchaToken)) {
            redirectAttributes.addFlashAttribute("successMessage", "Captcha validation failed.");
            return "redirect:/contact";
        }
        registeredClientService.createContactInquiry(name, email, phone, subject, message);
        redirectAttributes.addFlashAttribute("successMessage", "Message sent successfully. Our team will reply soon.");
        return "redirect:/contact";
    }

    @PostMapping("/tour-operators/request")
    public String submitTourOperatorRequest(@RequestParam String companyName,
                                            @RequestParam String contactName,
                                            @RequestParam String contactEmail,
                                            @RequestParam(required = false) String contactPhone,
                                            @RequestParam(required = false) String country,
                                            @RequestParam(required = false) String requestedExperienceSlug,
                                            @RequestParam(required = false) Integer estimatedGroupSize,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate estimatedDate,
                                            @RequestParam(defaultValue = "true") Boolean invoiceRequired,
                                            @RequestParam(required = false) String requestDetails,
                                            @RequestParam(required = false) String captchaToken,
                                            RedirectAttributes redirectAttributes) {
        if (!captchaService.verify(captchaToken)) {
            redirectAttributes.addFlashAttribute("successMessage", "Captcha validation failed.");
            return "redirect:/contact";
        }
        TourOperatorRequest request = registeredClientService.createTourOperatorRequest(
                companyName, contactName, contactEmail, contactPhone, country,
                requestedExperienceSlug, estimatedGroupSize, estimatedDate, invoiceRequired, requestDetails
        );
        redirectAttributes.addFlashAttribute("successMessage", "Tour-operator request submitted. Reference #" + request.getId() + ".");
        return "redirect:/contact";
    }

    private java.util.Optional<User> currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return java.util.Optional.empty();
        }
        return registeredClientService.findUserByEmail(authentication.getName());
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated() && authentication.getName() != null && !"anonymousUser".equals(authentication.getName());
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        if (!isAuthenticated(authentication)) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
