package com.volcanoartscenter.platform.web.internal.opsmanager.controller;

import com.volcanoartscenter.platform.shared.model.Booking;
import com.volcanoartscenter.platform.shared.model.ContactInquiry;
import com.volcanoartscenter.platform.shared.model.ShippingOrder;
import com.volcanoartscenter.platform.shared.model.TalentApplication;
import com.volcanoartscenter.platform.shared.model.TourOperatorRequest;
import com.volcanoartscenter.platform.shared.model.AvailabilitySlot;
import com.volcanoartscenter.platform.web.internal.opsmanager.service.OpsManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class OpsManagerController {

    private final OpsManagerService opsManagerService;

    @GetMapping("/admin/ops/dashboard")
    public String opsDashboard(Model model) {
        var bookings = opsManagerService.listBookings();
        var orders = opsManagerService.listShippingOrders();
        var inquiries = opsManagerService.listContactInquiries();
        var operatorRequests = opsManagerService.listOperatorRequests();
        var talentApplications = opsManagerService.listTalentApplications();
        model.addAttribute("adminPage", "ops-dashboard");
        model.addAttribute("pageTitle", "Operations Dashboard");
        model.addAttribute("totalBookings", opsManagerService.totalBookings());
        model.addAttribute("totalOrders", opsManagerService.totalOrders());
        model.addAttribute("totalInquiries", opsManagerService.totalInquiries());
        model.addAttribute("pendingTalentApplications", opsManagerService.pendingTalentApplications());
        model.addAttribute("pendingBookingsCount", bookings.stream().filter(b -> b.getStatus() == Booking.BookingStatus.PENDING).count());
        model.addAttribute("confirmedBookingsCount", bookings.stream().filter(b -> b.getStatus() == Booking.BookingStatus.CONFIRMED).count());
        model.addAttribute("completedBookingsCount", bookings.stream().filter(b -> b.getStatus() == Booking.BookingStatus.COMPLETED).count());
        model.addAttribute("processingOrdersCount", orders.stream().filter(o -> o.getStatus() == ShippingOrder.OrderStatus.PENDING || o.getStatus() == ShippingOrder.OrderStatus.PROCESSING).count());
        model.addAttribute("shippedOrdersCount", orders.stream().filter(o -> o.getStatus() == ShippingOrder.OrderStatus.SHIPPED || o.getStatus() == ShippingOrder.OrderStatus.IN_TRANSIT).count());
        model.addAttribute("deliveredOrdersCount", orders.stream().filter(o -> o.getStatus() == ShippingOrder.OrderStatus.DELIVERED).count());
        model.addAttribute("openInquiriesCount", inquiries.stream().filter(i -> i.getStatus() != ContactInquiry.InquiryStatus.CLOSED).count());
        model.addAttribute("openOperatorRequestsCount", operatorRequests.stream()
                .filter(r -> r.getStatus() != TourOperatorRequest.RequestStatus.CONFIRMED
                        && r.getStatus() != TourOperatorRequest.RequestStatus.DECLINED)
                .count());
        model.addAttribute("recentBookings", bookings.stream().limit(5).toList());
        model.addAttribute("recentOrders", orders.stream().limit(5).toList());
        model.addAttribute("recentInquiries", inquiries.stream().limit(4).toList());
        model.addAttribute("recentOperatorRequests", operatorRequests.stream().limit(4).toList());
        model.addAttribute("recentTalentApplications", talentApplications.stream().limit(4).toList());
        return "internal/ops-manager/dashboard";
    }

    @GetMapping("/admin/ops/bookings")
    public String opsBookings(Model model) {
        var bookings = opsManagerService.listBookings();
        model.addAttribute("adminPage", "bookings");
        model.addAttribute("pageTitle", "Bookings");
        model.addAttribute("totalBookings", opsManagerService.totalBookings());
        model.addAttribute("totalOrders", opsManagerService.totalOrders());
        model.addAttribute("totalInquiries", opsManagerService.totalInquiries());
        model.addAttribute("pendingTalentApplications", opsManagerService.pendingTalentApplications());
        model.addAttribute("pendingBookingsCount", bookings.stream().filter(b -> b.getStatus() == Booking.BookingStatus.PENDING).count());
        model.addAttribute("confirmedBookingsCount", bookings.stream().filter(b -> b.getStatus() == Booking.BookingStatus.CONFIRMED).count());
        model.addAttribute("completedBookingsCount", bookings.stream().filter(b -> b.getStatus() == Booking.BookingStatus.COMPLETED).count());
        model.addAttribute("unpaidBookingsCount", bookings.stream().filter(b -> b.getPaymentStatus() != Booking.PaymentStatus.PAID).count());
        model.addAttribute("items", bookings);
        model.addAttribute("bookingStatuses", Booking.BookingStatus.values());
        model.addAttribute("paymentStatuses", Booking.PaymentStatus.values());
        model.addAttribute("bookingExperienceTitles", bookings.stream().collect(java.util.stream.Collectors.toMap(
                Booking::getId,
                booking -> booking.getExperience() == null || booking.getExperience().getTitle() == null ? "-" : booking.getExperience().getTitle()
        )));
        return "internal/ops-manager/bookings";
    }

    @GetMapping("/admin/ops/bookings/{reference}")
    public String opsBookingDetail(@PathVariable String reference, Model model) {
        var booking = opsManagerService.getBookingByReference(reference);
        model.addAttribute("adminPage", "bookings");
        model.addAttribute("pageTitle", "Booking: " + reference);
        model.addAttribute("booking", booking);
        model.addAttribute("bookingStatuses", Booking.BookingStatus.values());
        model.addAttribute("paymentStatuses", Booking.PaymentStatus.values());
        return "internal/ops-manager/booking-detail";
    }

    @GetMapping("/admin/ops/donations")
    public String opsDonations(Model model) {
        model.addAttribute("adminPage", "donations");
        model.addAttribute("pageTitle", "Donations");
        model.addAttribute("items", opsManagerService.listDonations());
        return "internal/ops-manager/donations";
    }

    @GetMapping("/admin/ops/talent-applications")
    public String opsTalentApplications(Model model) {
        model.addAttribute("adminPage", "talent-applications");
        model.addAttribute("pageTitle", "Talent Applications");
        model.addAttribute("items", opsManagerService.listTalentApplications());
        return "internal/ops-manager/talent-applications";
    }
    @GetMapping("/admin/ops/shipping-orders")
    public String opsShippingOrders(Model model) {
        var orders = opsManagerService.listShippingOrders();
        model.addAttribute("adminPage", "shipping-orders");
        model.addAttribute("pageTitle", "Shipping Orders");
        model.addAttribute("items", orders);
        model.addAttribute("clientProfiles", opsManagerService.listClientProfiles());
        model.addAttribute("processingOrdersCount", orders.stream().filter(o -> o.getStatus() == ShippingOrder.OrderStatus.PENDING || o.getStatus() == ShippingOrder.OrderStatus.PROCESSING).count());
        model.addAttribute("shippedOrdersCount", orders.stream().filter(o -> o.getStatus() == ShippingOrder.OrderStatus.SHIPPED || o.getStatus() == ShippingOrder.OrderStatus.IN_TRANSIT).count());
        model.addAttribute("deliveredOrdersCount", orders.stream().filter(o -> o.getStatus() == ShippingOrder.OrderStatus.DELIVERED).count());
        model.addAttribute("unpaidOrdersCount", orders.stream().filter(o -> o.getPaymentStatus() != ShippingOrder.PaymentStatus.PAID).count());
        return "internal/ops-manager/shipping-orders";
    }

    @GetMapping("/admin/ops/contact-inquiries")
    public String opsContactInquiries(Model model) {
        model.addAttribute("adminPage", "contact-inquiries");
        model.addAttribute("pageTitle", "Contact Inquiries");
        model.addAttribute("items", opsManagerService.listContactInquiries());
        return "internal/ops-manager/contact-inquiries";
    }

    @GetMapping("/admin/ops/operator-requests")
    public String opsOperatorRequests(Model model) {
        model.addAttribute("adminPage", "operator-requests");
        model.addAttribute("pageTitle", "Tour Operator Requests");
        model.addAttribute("items", opsManagerService.listOperatorRequests());
        return "internal/ops-manager/operator-requests";
    }

    @GetMapping("/admin/ops/availability-slots")
    public String opsAvailabilitySlots(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                       @RequestParam(required = false) Long experienceId,
                                       Model model) {
        var items = opsManagerService.listAvailabilitySlots(fromDate, toDate, experienceId);
        var blackouts = opsManagerService.listBlackoutDates(experienceId);
        var experiences = opsManagerService.listExperiences();
        String selectedExperienceLabel = experiences.stream()
                .filter(exp -> experienceId != null && exp.getId().equals(experienceId))
                .map(com.volcanoartscenter.platform.shared.model.Experience::getTitle)
                .findFirst()
                .orElse("All experiences");
        long guidedSlotsCount = items.stream()
                .filter(slot -> slot.getAssignedGuideName() != null && !slot.getAssignedGuideName().isBlank())
                .count();
        long blockedSlotsCount = items.stream()
                .filter(slot -> slot.getStatus() == AvailabilitySlot.SlotStatus.FULLY_BOOKED
                        || slot.getStatus() == AvailabilitySlot.SlotStatus.REQUEST_ONLY)
                .count();
        model.addAttribute("adminPage", "availability-slots");
        model.addAttribute("pageTitle", "Availability Slots");
        model.addAttribute("items", items);
        model.addAttribute("experiences", experiences);
        model.addAttribute("blackouts", blackouts);
        model.addAttribute("guides", opsManagerService.listGuideUsers());
        model.addAttribute("slotStatuses", AvailabilitySlot.SlotStatus.values());
        model.addAttribute("selectedExperienceLabel", selectedExperienceLabel);
        model.addAttribute("guidedSlotsCount", guidedSlotsCount);
        model.addAttribute("blockedSlotsCount", blockedSlotsCount);
        model.addAttribute("slotExperienceTitles", items.stream().collect(java.util.stream.Collectors.toMap(
                com.volcanoartscenter.platform.shared.model.AvailabilitySlot::getId,
                slot -> slot.getExperience() == null || slot.getExperience().getTitle() == null ? "-" : slot.getExperience().getTitle()
        )));
        model.addAttribute("blackoutExperienceTitles", blackouts.stream().collect(java.util.stream.Collectors.toMap(
                com.volcanoartscenter.platform.shared.model.BlackoutDate::getId,
                blackout -> blackout.getExperience() == null || blackout.getExperience().getTitle() == null ? "-" : blackout.getExperience().getTitle()
        )));
        model.addAttribute("slotBookingsById", items.stream().collect(java.util.stream.Collectors.toMap(
                com.volcanoartscenter.platform.shared.model.AvailabilitySlot::getId,
                slot -> opsManagerService.bookingsForSlot(
                        slot.getExperience() == null ? null : slot.getExperience().getId(),
                        slot.getSlotDate()
                )
        )));
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("experienceId", experienceId);
        return "internal/ops-manager/availability-slots";
    }

    @PostMapping("/admin/ops/availability-slots/generate")
    public String generateOpsAvailability(@RequestParam Long experienceId,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                          @RequestParam(defaultValue = "15") Integer capacity,
                                          RedirectAttributes redirectAttributes) {
        opsManagerService.generateAvailability(experienceId, fromDate, toDate, capacity);
        redirectAttributes.addFlashAttribute("successMessage", "Availability generated.");
        return "redirect:/admin/ops/availability-slots";
    }

    @PostMapping("/admin/ops/availability-slots/blackout")
    public String addOpsBlackout(@RequestParam Long experienceId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                 @RequestParam(required = false) String reason,
                                 RedirectAttributes redirectAttributes) {
        opsManagerService.addBlackoutDate(experienceId, date, reason);
        redirectAttributes.addFlashAttribute("successMessage", "Blackout date added.");
        return "redirect:/admin/ops/availability-slots";
    }

    @PostMapping("/admin/ops/availability-slots/{id}/assign-guide")
    public String assignGuide(@PathVariable Long id,
                              @RequestParam(required = false) String guideEmail,
                              @RequestParam(required = false) String guideName,
                              RedirectAttributes redirectAttributes) {
        try {
            opsManagerService.assignGuide(id, guideEmail, guideName);
            redirectAttributes.addFlashAttribute("successMessage", "Guide assignment updated.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/ops/availability-slots";
    }

    @PostMapping("/admin/ops/availability-slots/{id}/update")
    public String updateSlot(@PathVariable Long id,
                             @RequestParam(required = false) com.volcanoartscenter.platform.shared.model.AvailabilitySlot.SlotStatus status,
                             @RequestParam(required = false) Integer maxCapacity,
                             @RequestParam(required = false) Integer bookedCount,
                             RedirectAttributes redirectAttributes) {
        opsManagerService.updateSlot(id, status, maxCapacity, bookedCount);
        redirectAttributes.addFlashAttribute("successMessage", "Slot updated.");
        return "redirect:/admin/ops/availability-slots";
    }

    @PostMapping("/admin/ops/availability-slots/blackout/{id}/delete")
    public String removeBlackout(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        opsManagerService.removeBlackout(id);
        redirectAttributes.addFlashAttribute("successMessage", "Blackout removed.");
        return "redirect:/admin/ops/availability-slots";
    }

    @PostMapping("/admin/ops/bookings/{id}/status")
    public String updateOpsBookingStatus(@PathVariable Long id,
                                          @RequestParam Booking.BookingStatus status,
                                          @RequestParam(required = false) Booking.PaymentStatus paymentStatus,
                                          @RequestParam(required = false) String adminNotes,
                                          @RequestParam(required = false) String notifyChannel,
                                          @RequestParam(required = false) String origin,
                                          Authentication authentication,
                                          RedirectAttributes redirectAttributes) {
        String actor = authentication == null ? "system" : authentication.getName();
        try {
            var booking = opsManagerService.updateBookingStatus(id, status, paymentStatus, adminNotes, notifyChannel, actor);
            redirectAttributes.addFlashAttribute("successMessage", "Booking status updated.");
            if ("detail".equals(origin)) {
                return "redirect:/admin/ops/bookings/" + booking.getBookingReference();
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/ops/bookings";
    }
    @PostMapping("/admin/ops/contact-inquiries/{id}/status")
    public String updateOpsInquiryStatus(@PathVariable Long id,
                                         @RequestParam ContactInquiry.InquiryStatus status,
                                         @RequestParam(required = false) String notifyChannel,
                                         Authentication authentication,
                                         RedirectAttributes redirectAttributes) {
        String actor = authentication == null ? "system" : authentication.getName();
        opsManagerService.updateInquiryStatus(id, status, notifyChannel, actor);
        redirectAttributes.addFlashAttribute("successMessage", "Inquiry status updated.");
        return "redirect:/admin/ops/contact-inquiries";
    }

    @PostMapping("/admin/ops/operator-requests/{id}/status")
    public String updateOpsOperatorRequestStatus(@PathVariable Long id,
                                                  @RequestParam TourOperatorRequest.RequestStatus status,
                                                  @RequestParam(required = false) java.math.BigDecimal partnerPrice,
                                                  @RequestParam(required = false) String partnerPriceCurrency,
                                                  @RequestParam(required = false) String itineraryAssetUrl,
                                                  @RequestParam(required = false) String adminNotes,
                                                  @RequestParam(required = false) String notifyChannel,
                                                  Authentication authentication,
                                                  RedirectAttributes redirectAttributes) {
        String actor = authentication == null ? "system" : authentication.getName();
        opsManagerService.updateOperatorRequest(id, status, partnerPrice, partnerPriceCurrency, itineraryAssetUrl, adminNotes, notifyChannel, actor);
        redirectAttributes.addFlashAttribute("successMessage", "Operator request status updated.");
        return "redirect:/admin/ops/operator-requests";
    }

    @PostMapping("/admin/ops/shipping-orders/{id}/status")
    public String updateOpsShippingOrderStatus(@PathVariable Long id,
                                               @RequestParam ShippingOrder.OrderStatus status,
                                               @RequestParam(required = false) String trackingNumber,
                                               @RequestParam(required = false) String adminNotes,
                                               @RequestParam(required = false) String notifyChannel,
                                               Authentication authentication,
                                               RedirectAttributes redirectAttributes) {
        String actor = authentication == null ? "system" : authentication.getName();
        try {
            opsManagerService.updateShippingOrderStatus(id, status, trackingNumber, adminNotes, notifyChannel, actor);
            redirectAttributes.addFlashAttribute("successMessage", "Shipping order updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/ops/shipping-orders";
    }

    @PostMapping("/admin/ops/shipping-orders/manual")
    public String createManualOrder(@RequestParam Long userId,
                                    @RequestParam Long productId,
                                    @RequestParam Integer quantity,
                                    @RequestParam java.math.BigDecimal totalAmount,
                                    @RequestParam(required = false) ShippingOrder.OrderStatus status,
                                    @RequestParam(required = false) ShippingOrder.PaymentStatus paymentStatus,
                                    @RequestParam(required = false) String adminNotes,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        String actor = authentication == null ? "system" : authentication.getName();
        try {
            ShippingOrder.OrderStatus initialStatus = status != null ? status : ShippingOrder.OrderStatus.PENDING;
            ShippingOrder.PaymentStatus initialPaymentStatus = paymentStatus != null ? paymentStatus : ShippingOrder.PaymentStatus.UNPAID;
            opsManagerService.createManualOrder(userId, productId, quantity, totalAmount, initialStatus, initialPaymentStatus, adminNotes, actor);
            redirectAttributes.addFlashAttribute("successMessage", "Manual order created successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to create order: " + ex.getMessage());
        }
        return "redirect:/admin/ops/shipping-orders";
    }

    @PostMapping("/admin/ops/talent-applications/{id}/status")
    public String updateOpsTalentApplicationStatus(@PathVariable Long id,
                                                   @RequestParam TalentApplication.ApplicationStatus status,
                                                   @RequestParam(required = false) String adminNotes,
                                                   @RequestParam(required = false) String notifyChannel,
                                                   Authentication authentication,
                                                   RedirectAttributes redirectAttributes) {
        String actor = authentication == null ? "system" : authentication.getName();
        opsManagerService.updateTalentApplicationStatus(id, status, adminNotes, notifyChannel, actor);
        redirectAttributes.addFlashAttribute("successMessage", "Talent application updated.");
        return "redirect:/admin/ops/talent-applications";
    }
}
