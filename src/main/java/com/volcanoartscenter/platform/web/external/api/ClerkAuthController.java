package com.volcanoartscenter.platform.web.external.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcanoartscenter.platform.security.clerk.ClerkUserSyncService;
import com.volcanoartscenter.platform.shared.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth/clerk")
@RequiredArgsConstructor
public class ClerkAuthController {

    private final ClerkUserSyncService clerkUserSyncService;
    private final ObjectMapper objectMapper;

    @Value("${platform.integrations.clerk.issuer:}")
    private String clerkIssuer;

    @Value("${platform.integrations.clerk.webhook-secret:}")
    private String webhookSecret;

    public record SessionRequest(String token) {}

    @PostMapping("/session")
    public ResponseEntity<Map<String, Object>> createSpringSession(@RequestBody SessionRequest request,
                                                                   HttpServletRequest servletRequest,
                                                                   HttpServletResponse servletResponse) {
        if (clerkIssuer == null || clerkIssuer.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "CLERK_ISSUER is not configured"));
        }
        if (request == null || request.token() == null || request.token().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing Clerk session token"));
        }
        Jwt jwt = NimbusJwtDecoder.withIssuerLocation(clerkIssuer).build().decode(request.token());
        String email = first(jwt.getClaimAsString("email"), jwt.getClaimAsString("email_address"));
        if (email == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Clerk JWT must include an email claim"));
        }
        Set<String> roles = rolesFromClaims(jwt);
        User user = clerkUserSyncService.syncUser(
                jwt.getSubject(),
                email,
                jwt.getClaimAsString("first_name"),
                jwt.getClaimAsString("last_name"),
                roles);
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .collect(Collectors.toSet());
        var auth = new UsernamePasswordAuthenticationToken(user.getEmail(), "CLERK", authorities);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        HttpSession session = servletRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return ResponseEntity.ok(Map.of("ok", true, "email", user.getEmail()));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleClerkWebhook(@RequestHeader(value = "svix-id", required = false) String svixId,
                                                     @RequestHeader(value = "svix-timestamp", required = false) String svixTimestamp,
                                                     @RequestHeader(value = "svix-signature", required = false) String svixSignature,
                                                     @RequestBody byte[] body) throws Exception {
        String payload = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        if (!verifySvix(svixId, svixTimestamp, svixSignature, payload)) {
            return ResponseEntity.badRequest().body("invalid signature");
        }
        JsonNode root = objectMapper.readTree(payload);
        String type = root.path("type").asText("");
        JsonNode data = root.path("data");
        if ("user.deleted".equals(type)) {
            clerkUserSyncService.deleteByClerkUserId(data.path("id").asText(null));
        } else if (type.startsWith("user.")) {
            clerkUserSyncService.syncFromClerkJson(data);
        }
        return ResponseEntity.ok("ok");
    }

    private Set<String> rolesFromClaims(Jwt jwt) {
        Object metadata = jwt.getClaims().get("public_metadata");
        if (metadata instanceof Map<?, ?> map) {
            JsonNode node = objectMapper.valueToTree(map);
            return clerkUserSyncService.rolesFrom(node);
        }
        Object role = jwt.getClaims().get("role");
        if (role instanceof String value) {
            return Set.of(value);
        }
        Object roles = jwt.getClaims().get("roles");
        if (roles instanceof java.util.Collection<?> values) {
            return values.stream().map(String::valueOf).collect(Collectors.toSet());
        }
        return Set.of();
    }

    private boolean verifySvix(String id, String timestamp, String signature, String payload) throws Exception {
        if (webhookSecret == null || webhookSecret.isBlank()) return false;
        if (id == null || timestamp == null || signature == null) return false;
        long ageSeconds = Math.abs(Instant.now().getEpochSecond() - Long.parseLong(timestamp));
        if (ageSeconds > 300) return false;
        String secret = webhookSecret.startsWith("whsec_") ? webhookSecret.substring(6) : webhookSecret;
        byte[] key = Base64.getDecoder().decode(secret);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        String signedPayload = id + "." + timestamp + "." + payload;
        String expected = Base64.getEncoder().encodeToString(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        for (String part : signature.split(" ")) {
            String candidate = part.startsWith("v1,") ? part.substring(3) : part;
            if (java.security.MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    private String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
