package com.volcanoartscenter.platform.web.external.registeredclient.controller;

import com.volcanoartscenter.platform.shared.messaging.Conversation;
import com.volcanoartscenter.platform.shared.messaging.MessagingService;
import com.volcanoartscenter.platform.shared.model.Booking;
import com.volcanoartscenter.platform.shared.model.Product;
import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.repository.ProductRepository;
import com.volcanoartscenter.platform.web.external.registeredclient.service.RegisteredClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class RegisteredClientDashboardController {

    private final RegisteredClientService registeredClientService;
    private final MessagingService messagingService;
    private final ProductRepository productRepository;

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("currentPage", "home");
        model.addAttribute("pageTitle", "Create Client Account");
        return "external/registered-client/register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String firstName,
                           @RequestParam String lastName,
                           @RequestParam String email,
                           @RequestParam(required = false) String phone,
                           @RequestParam(required = false) String country,
                           @RequestParam String password,
                           RedirectAttributes redirectAttributes) {
        try {
            registeredClientService.registerClientAccount(firstName, lastName, email, phone, country, password);
            redirectAttributes.addFlashAttribute("successMessage", "Client account created. Please sign in.");
            return "redirect:/login";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
            return "redirect:/register";
        }
    }

    @GetMapping("/client/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("currentPage", "client-dashboard");
        model.addAttribute("pageTitle", "Client Dashboard");
        model.addAttribute("workspaceSection", "Dashboard");
        model.addAttribute("workspaceSubtitle", "Protected workspace");
        populateClientWorkspaceModel(user, model);
        var inboxPage = messagingService.inboxForUser(user.getId(), 0, 20);
        model.addAttribute("clientConversations", inboxPage.getContent());
        model.addAttribute("clientConversationProductNames", productNamesFor(inboxPage.getContent()));
        model.addAttribute("clientConversationHasMore", inboxPage.hasNext());
        return "external/registered-client/dashboard";
    }

    @GetMapping("/client/bookings")
    public String bookings(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        if (user == null) {
            return "redirect:/login";
        }
        populateClientWorkspaceModel(user, model);
        List<Booking> bookings = registeredClientService.bookingsForUser(user);
        model.addAttribute("currentPage", "client-bookings");
        model.addAttribute("pageTitle", "My Bookings");
        model.addAttribute("workspaceSection", "Bookings");
        model.addAttribute("workspaceSubtitle", "Full reservation history and booking statuses");
        model.addAttribute("pendingBookingCount", bookings.stream().filter(b -> b.getStatus() == Booking.BookingStatus.PENDING).count());
        model.addAttribute("confirmedBookingCount", bookings.stream().filter(b -> b.getStatus() == Booking.BookingStatus.CONFIRMED).count());
        model.addAttribute("completedBookingCount", bookings.stream().filter(b -> b.getStatus() == Booking.BookingStatus.COMPLETED).count());
        model.addAttribute("cancelledBookingCount", bookings.stream().filter(b -> b.getStatus() == Booking.BookingStatus.CANCELLED).count());
        model.addAttribute("paidBookingCount", bookings.stream().filter(b -> b.getPaymentStatus() == Booking.PaymentStatus.PAID).count());
        return "external/registered-client/bookings";
    }

    @GetMapping("/client/bookings/{bookingReference}")
    public String bookingDetail(Authentication authentication,
                                @org.springframework.web.bind.annotation.PathVariable String bookingReference,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        if (user == null) {
            return "redirect:/login";
        }
        Booking booking = registeredClientService.bookingForUser(user, bookingReference).orElse(null);
        if (booking == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Booking not found in your account.");
            return "redirect:/client/bookings";
        }
        populateClientWorkspaceModel(user, model);
        model.addAttribute("currentPage", "client-bookings");
        model.addAttribute("pageTitle", "Booking " + booking.getBookingReference());
        model.addAttribute("workspaceSection", "Booking Details");
        model.addAttribute("workspaceSubtitle", "Full reservation record for your experience booking");
        model.addAttribute("booking", booking);
        return "external/registered-client/booking-detail";
    }

    @PostMapping("/client/profile")
    public String updateProfile(Authentication authentication,
                                @RequestParam String firstName,
                                @RequestParam String lastName,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String country,
                                RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        if (user == null) {
            return "redirect:/login";
        }
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhone(phone);
        user.setCountry(country);
        registeredClientService.saveUserProfile(user);
        redirectAttributes.addFlashAttribute("successMessage", "Profile updated.");
        return "redirect:/client/dashboard";
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return registeredClientService.findUserByEmail(authentication.getName()).orElse(null);
    }

    private void populateClientWorkspaceModel(User user, Model model) {
        model.addAttribute("userEmail", user.getEmail());
        model.addAttribute("user", user);
        model.addAttribute("orders", registeredClientService.ordersForUser(user));
        model.addAttribute("bookings", registeredClientService.bookingsForUser(user));
        model.addAttribute("donations", registeredClientService.donationsForUser(user));
        model.addAttribute("reviews", registeredClientService.reviewsForUser(user));
        model.addAttribute("savedItems", registeredClientService.savedItemsForUser(user));
    }

    private Map<Long, String> productNamesFor(List<Conversation> conversations) {
        List<Long> ids = conversations.stream()
                .map(Conversation::getProductId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));
    }
}
