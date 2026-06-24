package com.volcanoartscenter.platform.shared.email;

import com.volcanoartscenter.platform.shared.model.Booking;
import com.volcanoartscenter.platform.shared.model.ContactInquiry;
import com.volcanoartscenter.platform.shared.model.Donation;
import com.volcanoartscenter.platform.shared.model.ShippingOrder;
import com.volcanoartscenter.platform.shared.model.TalentApplication;
import com.volcanoartscenter.platform.shared.service.integration.OutboundChannelService;
import com.volcanoartscenter.platform.shared.model.NotificationLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionalEmailService {

    private final OutboundChannelService outboundChannelService;

    @Value("${ADMIN_NOTIFICATION_EMAIL:${platform.site.contact-email:hello@volcanoartsandhospes.com}}")
    private String adminEmail;

    @Value("${APP_BASE_URL:${platform.site.site-url:https://volcanoartscenter.rw}}")
    private String appBaseUrl;

    public void sendOrderPaid(ShippingOrder order) {
        if (order == null || order.getConfirmationEmailSentAt() != null) {
            return;
        }
        safeSend(order.getRecipientEmail(), "Order confirmed: " + order.getOrderReference(),
                brand("Your artwork order is confirmed",
                        "We received your payment and are preparing your Volcano Arts Center order.",
                        details(
                                row("Order", order.getOrderReference()),
                                row("Amount", money(order.getTotalAmount(), order.getCurrency())),
                                row("Status", String.valueOf(order.getStatus())),
                                row("Delivery country", order.getCountry())
                        ),
                        appBaseUrl + "/client/dashboard"));
        safeSend(adminEmail, "Paid art order: " + order.getOrderReference(),
                brand("Paid art order", "A customer completed payment for an art order.",
                        details(
                                row("Order", order.getOrderReference()),
                                row("Customer", order.getRecipientName() + " <" + order.getRecipientEmail() + ">"),
                                row("Amount", money(order.getTotalAmount(), order.getCurrency())),
                                row("Items", order.getOrderItems() == null ? "" : order.getOrderItems().stream()
                                        .map(i -> i.getQuantity() + " x " + i.getProductName())
                                        .collect(Collectors.joining(", ")))
                        ),
                        appBaseUrl + "/admin/ops/shipping-orders"));
    }

    public void sendBookingPaid(Booking booking) {
        if (booking == null || booking.getConfirmationEmailSentAt() != null) {
            return;
        }
        safeSend(booking.getGuestEmail(), "Booking confirmed: " + booking.getBookingReference(),
                brand("Your experience booking is confirmed",
                        "Payment was received for your Volcano Arts Center experience.",
                        details(
                                row("Booking", booking.getBookingReference()),
                                row("Experience", booking.getExperience() == null ? "" : booking.getExperience().getTitle()),
                                row("Date", String.valueOf(booking.getPreferredDate())),
                                row("Guests", String.valueOf(booking.getGroupSize())),
                                row("Payment", String.valueOf(booking.getPaymentStatus()))
                        ),
                        appBaseUrl + "/client/bookings/" + booking.getBookingReference()));
        safeSend(adminEmail, "Paid booking: " + booking.getBookingReference(),
                brand("Paid experience booking", "A guest completed payment for an experience.",
                        details(
                                row("Booking", booking.getBookingReference()),
                                row("Guest", booking.getGuestName() + " <" + booking.getGuestEmail() + ">"),
                                row("Date", String.valueOf(booking.getPreferredDate())),
                                row("Guests", String.valueOf(booking.getGroupSize()))
                        ),
                        appBaseUrl + "/admin/ops/bookings"));
    }

    public void sendDonationPaid(Donation donation) {
        if (donation == null || donation.getConfirmationEmailSentAt() != null) {
            return;
        }
        safeSend(donation.getDonorEmail(), "Donation receipt: " + donation.getReference(),
                brand("Thank you for supporting conservation",
                        "Your donation has been received by Volcano Arts Center.",
                        details(
                                row("Reference", donation.getReference()),
                                row("Amount", money(donation.getAmount(), donation.getCurrency())),
                                row("Campaign", donation.getCampaign() == null ? "General conservation" : donation.getCampaign().getName()),
                                row("Impact tier", donation.getImpactTierLabel())
                        ),
                        appBaseUrl + "/conservation"));
        safeSend(adminEmail, "Paid donation: " + donation.getReference(),
                brand("Paid conservation donation", "A donor completed a conservation donation.",
                        details(
                                row("Reference", donation.getReference()),
                                row("Donor", donation.getDonorName() + " <" + donation.getDonorEmail() + ">"),
                                row("Amount", money(donation.getAmount(), donation.getCurrency())),
                                row("Campaign", donation.getCampaign() == null ? "General" : donation.getCampaign().getName())
                        ),
                        appBaseUrl + "/admin/ops/donations"));
    }

    public void sendContactInquiry(ContactInquiry inquiry) {
        if (inquiry == null) return;
        safeSend(adminEmail, "New inquiry: " + safe(inquiry.getSubject()),
                brand("New contact inquiry", "A visitor submitted the contact form.",
                        details(
                                row("Name", inquiry.getFullName()),
                                row("Email", inquiry.getEmail()),
                                row("Phone", inquiry.getPhone()),
                                row("Subject", inquiry.getSubject()),
                                row("Message", inquiry.getMessage())
                        ),
                        appBaseUrl + "/admin/ops/contact-inquiries"));
    }

    public void sendTalentApplication(TalentApplication application) {
        if (application == null) return;
        safeSend(application.getEmail(), "Talent application received",
                brand("Application received", "Thank you for applying to the Volcano Arts Center talent program.",
                        details(row("Reference", "APP-" + application.getId()), row("Area", String.valueOf(application.getTalentArea()))),
                        appBaseUrl + "/talent/dashboard"));
        safeSend(adminEmail, "New talent application: " + safe(application.getFullName()),
                brand("New talent application", "A talent applicant submitted a new application.",
                        details(
                                row("Applicant", application.getFullName() + " <" + application.getEmail() + ">"),
                                row("Category", String.valueOf(application.getApplicantCategory())),
                                row("Area", String.valueOf(application.getTalentArea()))
                        ),
                        appBaseUrl + "/admin/ops/talent-applications"));
    }

    private void safeSend(String recipient, String subject, String html) {
        if (recipient == null || recipient.isBlank()) return;
        try {
            outboundChannelService.deliver(NotificationLog.Channel.EMAIL, recipient, subject, html);
        } catch (RuntimeException ex) {
            log.warn("Transactional email failed for {}: {}", recipient, ex.getMessage());
        }
    }

    private String brand(String title, String intro, String rows, String ctaUrl) {
        return """
                <div style="font-family:Arial,sans-serif;background:#f6f8f4;padding:24px">
                  <div style="max-width:640px;margin:auto;background:#ffffff;border:1px solid #dfe8db;padding:24px">
                    <p style="color:#00a651;font-weight:700;letter-spacing:.08em;text-transform:uppercase">Volcano Arts Center</p>
                    <h1 style="margin:0 0 12px;color:#163323;font-size:24px">%s</h1>
                    <p style="color:#32483a;line-height:1.6">%s</p>
                    <table style="width:100%%;border-collapse:collapse;margin:18px 0">%s</table>
                    <p><a href="%s" style="background:#00a651;color:#fff;padding:12px 16px;text-decoration:none;font-weight:700">View details</a></p>
                  </div>
                </div>
                """.formatted(escape(title), escape(intro), rows, escape(ctaUrl));
    }

    private String details(String... rows) {
        return String.join("", rows);
    }

    private String row(String label, String value) {
        return "<tr><td style=\"padding:8px;border-top:1px solid #edf2ea;color:#53645a\">" + escape(label)
                + "</td><td style=\"padding:8px;border-top:1px solid #edf2ea;color:#17251d;font-weight:700\">"
                + escape(safe(value)) + "</td></tr>";
    }

    private String money(BigDecimal amount, String currency) {
        return safe(currency) + " " + (amount == null ? "0.00" : amount.toPlainString());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String escape(String value) {
        return safe(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
