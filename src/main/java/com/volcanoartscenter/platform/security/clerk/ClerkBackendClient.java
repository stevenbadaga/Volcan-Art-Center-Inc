package com.volcanoartscenter.platform.security.clerk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ClerkBackendClient {

    private final String secretKey;
    private final RestClient restClient;

    public ClerkBackendClient(@Value("${platform.integrations.clerk.secret-key:}") String secretKey) {
        this.secretKey = secretKey;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.clerk.com/v1")
                .build();
    }

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    public String createUser(String email, String password, String firstName, String lastName, String role) {
        if (!isConfigured()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + secretKey)
                    .body(Map.of(
                            "email_address", List.of(email),
                            "password", password,
                            "first_name", firstName == null ? "" : firstName,
                            "last_name", lastName == null ? "" : lastName,
                            "public_metadata", Map.of("roles", List.of(role))
                    ))
                    .retrieve()
                    .body(Map.class);
            Object id = response == null ? null : response.get("id");
            return id == null ? null : id.toString();
        } catch (RestClientResponseException ex) {
            log.warn("Clerk user creation failed for {}: {}", email, ex.getStatusCode());
            throw new IllegalStateException("Clerk could not create this user. Please try again.");
        }
    }
}
