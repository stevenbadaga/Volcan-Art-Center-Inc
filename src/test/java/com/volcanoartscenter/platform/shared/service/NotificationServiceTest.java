package com.volcanoartscenter.platform.shared.service;

import com.volcanoartscenter.platform.shared.service.integration.IntegrationFacadeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private IntegrationFacadeService integrationFacadeService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(integrationFacadeService);
    }

    @Test
    void whatsappDispatchUsesRealFacadeAndNormalizesRecipient() {
        notificationService.sendWhatsAppAsync("+250 788 883 986", "Booking update", "Your booking is confirmed.");

        verify(integrationFacadeService).sendWhatsApp(
                "250788883986",
                "Booking update",
                "Your booking is confirmed.");
    }

    @Test
    void whatsappDispatchSupportsBodyOnlyMessages() {
        notificationService.sendWhatsAppAsync("+250 788 883 986", "Plain message only");

        verify(integrationFacadeService).sendWhatsApp(
                "250788883986",
                null,
                "Plain message only");
    }
}
