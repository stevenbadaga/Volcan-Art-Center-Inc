package com.volcanoartscenter.platform.web.internal.opsmanager.service;

import com.volcanoartscenter.platform.shared.model.ContactInquiry;
import com.volcanoartscenter.platform.shared.repository.AvailabilitySlotRepository;
import com.volcanoartscenter.platform.shared.repository.BlackoutDateRepository;
import com.volcanoartscenter.platform.shared.repository.BookingRepository;
import com.volcanoartscenter.platform.shared.repository.ContactInquiryRepository;
import com.volcanoartscenter.platform.shared.repository.DonationRepository;
import com.volcanoartscenter.platform.shared.repository.ExperienceRepository;
import com.volcanoartscenter.platform.shared.repository.ShippingOrderRepository;
import com.volcanoartscenter.platform.shared.repository.TalentApplicationRepository;
import com.volcanoartscenter.platform.shared.repository.TourOperatorRequestRepository;
import com.volcanoartscenter.platform.shared.repository.UserRepository;
import com.volcanoartscenter.platform.shared.service.AvailabilityService;
import com.volcanoartscenter.platform.shared.service.ComplianceService;
import com.volcanoartscenter.platform.shared.service.NotificationService;
import com.volcanoartscenter.platform.shared.service.integration.IntegrationFacadeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpsManagerServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private DonationRepository donationRepository;
    @Mock private TalentApplicationRepository talentApplicationRepository;
    @Mock private ShippingOrderRepository shippingOrderRepository;
    @Mock private ContactInquiryRepository contactInquiryRepository;
    @Mock private TourOperatorRequestRepository tourOperatorRequestRepository;
    @Mock private AvailabilitySlotRepository availabilitySlotRepository;
    @Mock private ExperienceRepository experienceRepository;
    @Mock private BlackoutDateRepository blackoutDateRepository;
    @Mock private UserRepository userRepository;
    @Mock private com.volcanoartscenter.platform.shared.repository.ProductRepository productRepository;
    @Mock private AvailabilityService availabilityService;
    @Mock private ComplianceService complianceService;
    @Mock private IntegrationFacadeService integrationFacadeService;
    @Mock private NotificationService notificationService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OpsManagerService opsManagerService;

    @BeforeEach
    void setUp() {
        opsManagerService = new OpsManagerService(
                bookingRepository,
                donationRepository,
                talentApplicationRepository,
                shippingOrderRepository,
                contactInquiryRepository,
                tourOperatorRequestRepository,
                availabilitySlotRepository,
                experienceRepository,
                blackoutDateRepository,
                userRepository,
                productRepository,
                availabilityService,
                complianceService,
                integrationFacadeService,
                notificationService,
                eventPublisher);
    }

    @Test
    void inquiryUpdatesCanDispatchWhatsAppNotifications() {
        ContactInquiry inquiry = ContactInquiry.builder()
                .id(12L)
                .fullName("Guest User")
                .email("guest@example.com")
                .phone("+250 788 883 986")
                .subject("General")
                .message("Hello")
                .status(ContactInquiry.InquiryStatus.NEW)
                .build();
        when(contactInquiryRepository.findById(12L)).thenReturn(Optional.of(inquiry));

        ContactInquiry updated = opsManagerService.updateInquiryStatus(
                12L,
                ContactInquiry.InquiryStatus.CLOSED,
                "WHATSAPP",
                "ops@example.com");

        assertEquals(ContactInquiry.InquiryStatus.CLOSED, updated.getStatus());
        verify(notificationService).sendWhatsAppAsync(
                "+250 788 883 986",
                "Inquiry update",
                "Your inquiry status is now CLOSED.");
    }
}
