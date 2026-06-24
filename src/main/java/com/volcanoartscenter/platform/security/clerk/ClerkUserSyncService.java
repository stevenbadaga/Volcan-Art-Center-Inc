package com.volcanoartscenter.platform.security.clerk;

import com.fasterxml.jackson.databind.JsonNode;
import com.volcanoartscenter.platform.shared.model.Role;
import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.repository.RoleRepository;
import com.volcanoartscenter.platform.shared.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClerkUserSyncService {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "SUPER_ADMIN", "CONTENT_MANAGER", "OPS_MANAGER",
            "REGISTERED_CLIENT", "TOUR_OPERATOR", "TALENT_APPLICANT");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User syncUser(String clerkUserId, String email, String firstName, String lastName, Set<String> roles) {
        String normalizedEmail = normalizeEmail(email);
        if (clerkUserId == null || clerkUserId.isBlank() || normalizedEmail == null) {
            throw new IllegalArgumentException("Clerk user id and email are required.");
        }
        User user = userRepository.findByClerkUserId(clerkUserId)
                .or(() -> userRepository.findByEmail(normalizedEmail))
                .orElseGet(() -> User.builder()
                        .email(normalizedEmail)
                        .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .enabled(true)
                        .build());
        user.setClerkUserId(clerkUserId);
        user.setEmail(normalizedEmail);
        user.setFirstName(nonBlank(firstName, user.getFirstName(), "Client"));
        user.setLastName(nonBlank(lastName, user.getLastName(), "Member"));
        user.setEnabled(true);

        Set<String> normalizedRoles = normalizeRoles(roles);
        if (normalizedRoles.isEmpty()) {
            normalizedRoles = Set.of("REGISTERED_CLIENT");
        }
        List<Role> localRoles = roleRepository.findByNameIn(normalizedRoles);
        user.setRoles(new HashSet<>(localRoles));
        return userRepository.save(user);
    }

    @Transactional
    public void deleteByClerkUserId(String clerkUserId) {
        if (clerkUserId == null || clerkUserId.isBlank()) return;
        userRepository.findByClerkUserId(clerkUserId).ifPresent(user -> {
            user.setEnabled(false);
            userRepository.save(user);
        });
    }

    public User syncFromClerkJson(JsonNode data) {
        String clerkId = text(data, "id");
        String email = primaryEmail(data);
        String firstName = text(data, "first_name");
        String lastName = text(data, "last_name");
        Set<String> roles = rolesFrom(data.get("public_metadata"));
        return syncUser(clerkId, email, firstName, lastName, roles);
    }

    public Set<String> rolesFrom(JsonNode metadata) {
        Set<String> roles = new HashSet<>();
        if (metadata == null || metadata.isMissingNode() || metadata.isNull()) {
            return roles;
        }
        addRole(roles, text(metadata, "role"));
        JsonNode rolesNode = metadata.get("roles");
        if (rolesNode != null && rolesNode.isArray()) {
            rolesNode.forEach(node -> addRole(roles, node.asText()));
        }
        return normalizeRoles(roles);
    }

    private Set<String> normalizeRoles(Set<String> roles) {
        Set<String> normalized = new HashSet<>();
        if (roles == null) return normalized;
        for (String role : roles) {
            addRole(normalized, role);
        }
        return normalized;
    }

    private void addRole(Set<String> roles, String role) {
        if (role == null || role.isBlank()) return;
        String normalized = role.trim().toUpperCase(Locale.ROOT).replace("ROLE_", "");
        if (ALLOWED_ROLES.contains(normalized)) {
            roles.add(normalized);
        }
    }

    private String primaryEmail(JsonNode data) {
        String primaryId = text(data, "primary_email_address_id");
        JsonNode emails = data.get("email_addresses");
        if (emails != null && emails.isArray()) {
            for (JsonNode emailNode : emails) {
                if (primaryId != null && primaryId.equals(text(emailNode, "id"))) {
                    return text(emailNode, "email_address");
                }
            }
            if (!emails.isEmpty()) {
                return text(emails.get(0), "email_address");
            }
        }
        return text(data, "email");
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) return null;
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value;
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String nonBlank(String preferred, String fallback, String defaultValue) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        if (fallback != null && !fallback.isBlank()) return fallback;
        return defaultValue;
    }
}
