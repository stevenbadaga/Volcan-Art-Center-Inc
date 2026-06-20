package com.volcanoartscenter.platform.shared.service;

import com.volcanoartscenter.platform.shared.service.integration.IntegrationFacadeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final IntegrationFacadeService integrationFacadeService;

    @Async("notificationTaskExecutor")
    public void sendEmailAsync(String to, String subject, String body) {
        log.info("Starting async email dispatch to: {}", to);
        try {
            integrationFacadeService.sendEmail(to, subject, body);
            log.info("Successfully dispatched email asynchronously to: {}", to);
        } catch (Exception e) {
            log.error("Failed to dispatch async email to: {}. Reason: {}", to, e.getMessage());
        }
    }

    @Async("notificationTaskExecutor")
    public void sendWhatsAppAsync(String recipient, String subject, String body) {
        String normalizedRecipient = normalizeWhatsappRecipient(recipient);
        log.info("Starting async WhatsApp dispatch to: {}", normalizedRecipient);
        try {
            integrationFacadeService.sendWhatsApp(normalizedRecipient, subject, body);
            log.info("Successfully dispatched WhatsApp asynchronously to: {}", normalizedRecipient);
        } catch (Exception e) {
            log.error("Failed to dispatch async WhatsApp message to: {}", normalizedRecipient, e);
        }
    }

    @Async("notificationTaskExecutor")
    public void sendWhatsAppAsync(String recipient, String message) {
        sendWhatsAppAsync(recipient, null, message);
    }

    private String normalizeWhatsappRecipient(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("WhatsApp recipient is required.");
        }
        String normalized = value.replaceAll("[^0-9]", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("WhatsApp recipient must contain digits.");
        }
        return normalized;
    }
}
