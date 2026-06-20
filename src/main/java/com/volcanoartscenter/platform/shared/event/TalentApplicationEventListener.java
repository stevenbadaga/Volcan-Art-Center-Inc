package com.volcanoartscenter.platform.shared.event;

import com.volcanoartscenter.platform.shared.model.TalentApplication;
import com.volcanoartscenter.platform.shared.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TalentApplicationEventListener {

    private final NotificationService notificationService;

    @EventListener
    public void handleTalentApplicationApproved(TalentApplicationApprovedEvent event) {
        TalentApplication app = event.getApplication();
        log.info("Event Listener caught approval for application: {}", app.getReferenceNumber());

        String subject = "Congratulations! Your Volcano Arts Center Application is Approved";
        String body = String.format("Dear %s,\n\nWe are thrilled to inform you that your portfolio (%s) has been approved! Over the next few days, our talent coordinators will reach out.", 
                                    app.getFullName(), app.getReferenceNumber());

        // Dispatch Email
        notificationService.sendEmailAsync(app.getEmail(), subject, body);

        // Dispatch WhatsApp if phone is available
        if (app.getPhone() != null && !app.getPhone().isEmpty()) {
            String waMessage = String.format("Hello %s! Volcano Arts Center has approved your talent portfolio '%s'. Please check your email for the next steps.", 
                                             app.getFullName(), app.getReferenceNumber());
            notificationService.sendWhatsAppAsync(app.getPhone(), waMessage);
        }
    }
}
