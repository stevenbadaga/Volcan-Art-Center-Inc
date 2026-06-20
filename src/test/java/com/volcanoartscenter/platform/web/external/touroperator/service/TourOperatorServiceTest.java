package com.volcanoartscenter.platform.web.external.touroperator.service;

import com.volcanoartscenter.platform.shared.model.Role;
import com.volcanoartscenter.platform.shared.model.TourOperatorRequest;
import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.repository.ExperienceRepository;
import com.volcanoartscenter.platform.shared.repository.ProductRepository;
import com.volcanoartscenter.platform.shared.repository.RoleRepository;
import com.volcanoartscenter.platform.shared.repository.TourOperatorRequestRepository;
import com.volcanoartscenter.platform.shared.repository.UserRepository;
import com.volcanoartscenter.platform.shared.service.ComplianceService;
import com.volcanoartscenter.platform.shared.service.integration.IntegrationFacadeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TourOperatorServiceTest {

    @Mock private TourOperatorRequestRepository tourOperatorRequestRepository;
    @Mock private ExperienceRepository experienceRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private ComplianceService complianceService;
    @Mock private IntegrationFacadeService integrationFacadeService;
    @Mock private PasswordEncoder passwordEncoder;

    private TourOperatorService tourOperatorService;

    @BeforeEach
    void setUp() {
        tourOperatorService = new TourOperatorService(
                tourOperatorRequestRepository,
                experienceRepository,
                productRepository,
                userRepository,
                roleRepository,
                complianceService,
                integrationFacadeService,
                passwordEncoder);
    }

    @Test
    void createRequestUsesWhatsAppWhenRequested() {
        Role operatorRole = Role.builder().name("TOUR_OPERATOR").description("Tour operator").build();
        User operator = User.builder()
                .id(5L)
                .email("operator@example.com")
                .firstName("Tour")
                .lastName("Operator")
                .roles(Set.of(operatorRole))
                .build();
        when(userRepository.findByEmail("operator@example.com")).thenReturn(Optional.of(operator));
        when(tourOperatorRequestRepository.save(any(TourOperatorRequest.class))).thenAnswer(invocation -> {
            TourOperatorRequest request = invocation.getArgument(0);
            request.setId(42L);
            return request;
        });

        TourOperatorRequest saved = tourOperatorService.createRequest(
                "operator@example.com",
                "Safari Co",
                "Alex",
                "+250 788 883 986",
                "Rwanda",
                TourOperatorRequest.RequestType.GROUP_BOOKING,
                "lake-kivu",
                8,
                null,
                true,
                "Need a custom package",
                "WHATSAPP");

        assertEquals("WHATSAPP", saved.getPreferredContactChannel());
        verify(integrationFacadeService).sendWhatsApp(
                "+250 788 883 986",
                "Request submitted",
                "Your request #42 has been received and will be reviewed.");
        verify(integrationFacadeService, never()).sendEmail(any(), any(), any());
    }
}
