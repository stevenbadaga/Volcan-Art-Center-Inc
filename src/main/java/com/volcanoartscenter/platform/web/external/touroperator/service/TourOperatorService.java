package com.volcanoartscenter.platform.web.external.touroperator.service;

import com.volcanoartscenter.platform.shared.model.Experience;
import com.volcanoartscenter.platform.shared.model.Product;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TourOperatorService {

    private final TourOperatorRequestRepository tourOperatorRequestRepository;
    private final ExperienceRepository experienceRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ComplianceService complianceService;
    private final IntegrationFacadeService integrationFacadeService;
    private final PasswordEncoder passwordEncoder;

    public List<TourOperatorRequest> listOwnerRequests(String ownerEmail) {
        String owner = normalize(ownerEmail);
        if (owner == null) {
            return List.of();
        }
        return tourOperatorRequestRepository.findByOwnerEmailIgnoreCaseOrderByCreatedAtDesc(owner);
    }

    public List<Experience> activeExperiences() {
        return experienceRepository.findByActiveTrueOrderByFeaturedDescTitleAsc();
    }

    public List<Product> availableProducts() {
        return productRepository.findByAvailableTrueOrderByFeaturedDescNameAsc();
    }

    public PortalStats buildStats(String ownerEmail) {
        List<TourOperatorRequest> requests = listOwnerRequests(ownerEmail);
        long submitted = requests.stream().filter(r -> r.getStatus() == TourOperatorRequest.RequestStatus.SUBMITTED).count();
        long review = requests.stream().filter(r -> r.getStatus() == TourOperatorRequest.RequestStatus.UNDER_REVIEW).count();
        long confirmed = requests.stream().filter(r -> r.getStatus() == TourOperatorRequest.RequestStatus.CONFIRMED).count();
        long invoicing = requests.stream().filter(r -> r.getStatus() == TourOperatorRequest.RequestStatus.INVOICE_PENDING).count();
        return new PortalStats(requests.size(), submitted, review, confirmed, invoicing);
    }

    @Transactional
    public User registerOperatorAccount(String companyName, String firstName, String lastName, String email, String phone, String country, String rawPassword) {
        String normalizedEmail = normalize(email);
        if (normalizedEmail == null) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must have at least 8 characters.");
        }
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }
        Role tourOperatorRole = roleRepository.findByName("TOUR_OPERATOR")
                .orElseThrow(() -> new IllegalStateException("TOUR_OPERATOR role is missing."));
        User user = User.builder()
                .email(normalizedEmail)
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .country(country)
                .enabled(true)
                .password(passwordEncoder.encode(rawPassword))
                .roles(new HashSet<>(List.of(tourOperatorRole)))
                .build();
        User saved = userRepository.save(user);
        complianceService.recordConsent(normalizedEmail, "TOUR_OPERATOR_ACCOUNT_TERMS", true, "tour-operator-registration");
        complianceService.audit(normalizedEmail, "TOUR_OPERATOR_ACCOUNT_CREATED", "User", saved.getId(), "Company=" + companyName);
        integrationFacadeService.sendEmail(normalizedEmail, "Welcome to Volcano Arts Center B2B",
                "Your tour operator account has been created. You can now submit and track group requests.");
        return saved;
    }

    @Transactional
    public TourOperatorRequest createRequest(String ownerEmail, String companyName, String contactName, String contactPhone, String country,
                                             TourOperatorRequest.RequestType requestType, String requestedExperienceSlug,
                                             Integer estimatedGroupSize, LocalDate estimatedDate, Boolean invoiceRequired,
                                             String requestDetails, String preferredContactChannel) {
        String owner = normalize(ownerEmail);
        if (owner == null) {
            throw new IllegalArgumentException("Operator email is required.");
        }
        User ownerUser = userRepository.findByEmail(owner).orElseThrow(() -> new IllegalArgumentException("Operator account not found."));
        if (ownerUser.getRoles().stream().noneMatch(role -> "TOUR_OPERATOR".equals(role.getName()))) {
            throw new IllegalArgumentException("User is not a tour operator.");
        }
        int safeGroup = estimatedGroupSize == null ? 1 : Math.max(1, estimatedGroupSize);
        TourOperatorRequest request = TourOperatorRequest.builder()
                .companyName(companyName)
                .contactName(contactName)
                .contactEmail(owner)
                .ownerEmail(owner)
                .contactPhone(contactPhone)
                .country(country)
                .requestType(requestType == null ? TourOperatorRequest.RequestType.GROUP_BOOKING : requestType)
                .requestedExperienceSlug(requestedExperienceSlug)
                .estimatedGroupSize(safeGroup)
                .estimatedDate(estimatedDate)
                .invoiceRequired(invoiceRequired == null || invoiceRequired)
                .requestDetails(requestDetails)
                .preferredContactChannel(channelOrDefault(preferredContactChannel))
                .partnerPriceCurrency("USD")
                .status(TourOperatorRequest.RequestStatus.SUBMITTED)
                .build();
        TourOperatorRequest saved = tourOperatorRequestRepository.save(request);
        complianceService.recordConsent(owner, "TOUR_OPERATOR_REQUEST_TERMS", true, "tour-operator-portal");
        complianceService.audit(owner, "TOUR_OPERATOR_REQUEST_CREATED", "TourOperatorRequest", saved.getId(),
                "Type=" + saved.getRequestType() + ", experience=" + saved.getRequestedExperienceSlug());
        notifyOperator(saved, saved.getPreferredContactChannel(), "Request submitted",
                "Your request #" + saved.getId() + " has been received and will be reviewed.");
        return saved;
    }

    @Transactional
    public TourOperatorRequest updateRequestDetails(String ownerEmail, Long requestId, Integer estimatedGroupSize, LocalDate estimatedDate,
                                                    String requestDetails, String preferredContactChannel) {
        TourOperatorRequest request = ownedRequest(ownerEmail, requestId);
        if (request.getStatus() == TourOperatorRequest.RequestStatus.CONFIRMED
                || request.getStatus() == TourOperatorRequest.RequestStatus.DECLINED) {
            throw new IllegalStateException("Finalized requests cannot be edited.");
        }
        if (estimatedGroupSize != null) {
            request.setEstimatedGroupSize(Math.max(1, estimatedGroupSize));
        }
        request.setEstimatedDate(estimatedDate);
        request.setRequestDetails(requestDetails);
        request.setPreferredContactChannel(channelOrDefault(preferredContactChannel));
        complianceService.audit(normalize(ownerEmail), "TOUR_OPERATOR_REQUEST_UPDATED", "TourOperatorRequest", requestId,
                "Operator updated request details.");
        return request;
    }

    @Transactional
    public TourOperatorRequest cancelRequest(String ownerEmail, Long requestId) {
        TourOperatorRequest request = ownedRequest(ownerEmail, requestId);
        if (request.getStatus() == TourOperatorRequest.RequestStatus.CONFIRMED) {
            throw new IllegalStateException("Confirmed requests cannot be cancelled here.");
        }
        request.setStatus(TourOperatorRequest.RequestStatus.DECLINED);
        request.setAdminNotes(appendNotes(request.getAdminNotes(), "Cancelled by operator at " + LocalDateTime.now()));
        complianceService.audit(normalize(ownerEmail), "TOUR_OPERATOR_REQUEST_CANCELLED", "TourOperatorRequest", requestId,
                "Operator cancelled request.");
        notifyOperator(request, request.getPreferredContactChannel(), "Request cancelled",
                "Request #" + request.getId() + " has been marked as cancelled.");
        return request;
    }

    @Transactional
    public void markNotifiedByOps(TourOperatorRequest request, String channel) {
        if (request == null) {
            return;
        }
        request.setLastNotifiedChannel(channelOrDefault(channel));
        request.setLastNotifiedAt(LocalDateTime.now());
    }

    public Optional<TourOperatorRequest> latestConfirmed(String ownerEmail) {
        return listOwnerRequests(ownerEmail).stream()
                .filter(r -> r.getStatus() == TourOperatorRequest.RequestStatus.CONFIRMED)
                .max(Comparator.comparing(TourOperatorRequest::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private TourOperatorRequest ownedRequest(String ownerEmail, Long requestId) {
        String owner = normalize(ownerEmail);
        TourOperatorRequest request = tourOperatorRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));
        if (owner == null || request.getOwnerEmail() == null || !owner.equalsIgnoreCase(request.getOwnerEmail())) {
            throw new IllegalArgumentException("Request does not belong to this operator.");
        }
        return request;
    }

    private String appendNotes(String current, String next) {
        if (current == null || current.isBlank()) {
            return next;
        }
        return current + "\n" + next;
    }

    private void notifyOperator(TourOperatorRequest request, String channel, String subject, String body) {
        String normalized = channelOrDefault(channel);
        if ("WHATSAPP".equals(normalized) && request.getContactPhone() != null && !request.getContactPhone().isBlank()) {
            integrationFacadeService.sendWhatsApp(request.getContactPhone(), subject, body);
            request.setLastNotifiedChannel("WHATSAPP");
            request.setLastNotifiedAt(LocalDateTime.now());
            return;
        }
        integrationFacadeService.sendEmail(request.getContactEmail(), subject, body);
        request.setLastNotifiedChannel("EMAIL");
        request.setLastNotifiedAt(LocalDateTime.now());
    }

    private String channelOrDefault(String channel) {
        String normalized = normalizeChannel(channel);
        if ("WHATSAPP".equals(normalized)) {
            return "WHATSAPP";
        }
        return "EMAIL";
    }

    private String normalizeChannel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record PortalStats(long total, long submitted, long underReview, long confirmed, long invoicePending) {}
}
