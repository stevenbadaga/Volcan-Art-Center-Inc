package com.volcanoartscenter.platform.web.external.talentapplicant.service;

import com.volcanoartscenter.platform.shared.model.TalentApplication;
import com.volcanoartscenter.platform.shared.model.Role;
import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.repository.RoleRepository;
import com.volcanoartscenter.platform.shared.repository.TalentApplicationRepository;
import com.volcanoartscenter.platform.shared.repository.UserRepository;
import com.volcanoartscenter.platform.shared.service.ComplianceService;
import com.volcanoartscenter.platform.security.clerk.ClerkBackendClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TalentApplicantService {

    private final TalentApplicationRepository talentApplicationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ComplianceService complianceService;
    private final PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private ClerkBackendClient clerkBackendClient;

    public User registerApplicantAccount(String firstName, String lastName, String email, String phone, String country, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must have at least 8 characters.");
        }
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }
        Role role = roleRepository.findByName("TALENT_APPLICANT")
                .orElseThrow(() -> new IllegalStateException("TALENT_APPLICANT role is missing."));
        String clerkUserId = clerkBackendClient == null ? null
                : clerkBackendClient.createUser(normalizedEmail, rawPassword, firstName, lastName, "TALENT_APPLICANT");
        User user = User.builder()
                .email(normalizedEmail)
                .clerkUserId(clerkUserId)
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .country(country)
                .enabled(true)
                .password(passwordEncoder.encode(rawPassword))
                .roles(new HashSet<>(List.of(role)))
                .build();
        User saved = userRepository.save(user);
        complianceService.recordConsent(normalizedEmail, "TALENT_ACCOUNT_TERMS", true, "talent-registration");
        complianceService.audit(normalizedEmail, "TALENT_ACCOUNT_CREATED", "User", saved.getId(), "Talent applicant account created.");
        return saved;
    }

    public Optional<User> findUserByEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized == null) {
            return Optional.empty();
        }
        return userRepository.findByEmail(normalized);
    }

    public List<TalentApplication> applicationsFor(User user) {
        return talentApplicationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public Optional<TalentApplication> latestFor(User user) {
        List<TalentApplication> list = applicationsFor(user);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public User saveProfile(User user) {
        return userRepository.save(user);
    }

    public TalentApplication createApplication(User user, String fullName, String email, String phone, String ageRange, String gender,
                                               String location, TalentApplication.ApplicantCategory applicantCategory,
                                               TalentApplication.TalentArea talentArea, String experienceDescription,
                                               String motivation, String availabilityDetails, String accessibilityNeeds,
                                               String preferredContactChannel) {
        String contactEmail = user == null ? email : user.getEmail();
        TalentApplication application = TalentApplication.builder()
                .user(user)
                .fullName(user == null ? fullName : user.getFullName())
                .email(contactEmail)
                .phone(user == null ? phone : user.getPhone())
                .ageRange(ageRange)
                .gender(gender)
                .location(user == null ? location : user.getCountry())
                .applicantCategory(applicantCategory)
                .talentArea(talentArea)
                .experienceDescription(experienceDescription)
                .motivation(motivation)
                .availabilityDetails(availabilityDetails)
                .accessibilityNeeds(accessibilityNeeds)
                .preferredContactChannel(normalizeChannel(preferredContactChannel))
                .status(TalentApplication.ApplicationStatus.PENDING)
                .build();
        TalentApplication saved = talentApplicationRepository.save(application);
        complianceService.recordConsent(contactEmail, "TALENT_APPLICATION_CONSENT", true, "talent-application-form");
        complianceService.audit(contactEmail, "TALENT_APPLICATION_CREATED", "TalentApplication", saved.getId(),
                "Category=" + applicantCategory + ", area=" + talentArea);
        return saved;
    }

    public TalentApplication updateOwnApplication(User user, Long id, String ageRange, String gender, String location,
                                                  String experienceDescription, String motivation, String availabilityDetails,
                                                  String accessibilityNeeds, String preferredContactChannel) {
        TalentApplication application = talentApplicationRepository.findById(id).orElseThrow();
        if (application.getUser() == null || user == null || !application.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Application ownership mismatch.");
        }
        if (application.getStatus() != TalentApplication.ApplicationStatus.PENDING
                && application.getStatus() != TalentApplication.ApplicationStatus.AWAITING_INFO) {
            throw new IllegalStateException("This application can no longer be edited.");
        }
        application.setAgeRange(ageRange);
        application.setGender(gender);
        application.setLocation(location);
        application.setExperienceDescription(experienceDescription);
        application.setMotivation(motivation);
        application.setAvailabilityDetails(availabilityDetails);
        application.setAccessibilityNeeds(accessibilityNeeds);
        application.setPreferredContactChannel(normalizeChannel(preferredContactChannel));
        complianceService.audit(user.getEmail(), "TALENT_APPLICATION_UPDATED", "TalentApplication", id, "Applicant updated profile data.");
        return application;
    }

    private String normalizeChannel(String value) {
        if (value == null || value.isBlank()) {
            return "EMAIL";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "WHATSAPP".equals(normalized) ? "WHATSAPP" : "EMAIL";
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
