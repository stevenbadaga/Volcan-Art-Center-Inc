package com.volcanoartscenter.platform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local-only startup diagnostics for integration config loading.
 * Logs presence/absence only; never logs raw secret values.
 */
@Component
@Profile("local")
@Slf4j
public class IntegrationEnvDiagnostics {

    @Value("${platform.integrations.clerk.publishable-key:}")
    private String clerkPublishableKey;

    @Value("${platform.integrations.clerk.secret-key:}")
    private String clerkSecretKey;

    @Value("${platform.integrations.clerk.issuer:}")
    private String clerkIssuer;

    @Value("${platform.integrations.clerk.webhook-secret:}")
    private String clerkWebhookSecret;

    @Value("${platform.integrations.resend.api-key:}")
    private String resendApiKey;

    @Value("${platform.integrations.resend.mail-from:}")
    private String resendFromEmail;

    @Value("${ADMIN_NOTIFICATION_EMAIL:}")
    private String adminNotificationEmail;

    @Value("${platform.notifications.email.enabled:true}")
    private boolean emailNotificationsEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void logPresence() {
        log.info("Integration env diagnostics (present=yes/missing=no): clerkPublishableKey={}, clerkSecretKey={}, clerkIssuer={}, clerkWebhookSecret={}, resendApiKey={}, resendFromEmail={}, adminNotificationEmail={}, emailNotificationsEnabled={}",
                present(clerkPublishableKey),
                present(clerkSecretKey),
                present(clerkIssuer),
                present(clerkWebhookSecret),
                present(resendApiKey),
                present(resendFromEmail),
                present(adminNotificationEmail),
                emailNotificationsEnabled);
    }

    private String present(String value) {
        return value != null && !value.isBlank() ? "yes" : "no";
    }
}
