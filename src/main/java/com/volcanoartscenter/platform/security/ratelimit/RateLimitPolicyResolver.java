package com.volcanoartscenter.platform.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class RateLimitPolicyResolver {

    private record Rule(String policy, String pathPrefix, Scope scope, String method) {}

    public enum Scope { IP, USER }

    public record Resolved(String policy, Scope scope) {}

    private static final List<Rule> RULES = List.of(
            new Rule("login", "/login", Scope.IP, "POST"),
            new Rule("login", "/api/v1/auth/login", Scope.IP, "POST"),
            new Rule("register", "/register", Scope.IP, "POST"),
            new Rule("register", "/api/v1/auth/register", Scope.IP, "POST"),
            new Rule("register", "/talent/register", Scope.IP, "POST"),
            new Rule("register", "/tour-operators/register", Scope.IP, "POST"),
            new Rule("password-reset", "/password-reset", Scope.IP, "POST"),
            new Rule("password-reset", "/api/v1/auth/password-reset", Scope.IP, "POST"),
            new Rule("contact-form", "/contact", Scope.IP, "POST"),
            new Rule("talent-form", "/talent/apply", Scope.IP, "POST"),
            new Rule("talent-form", "/api/v1/public/talent-applications", Scope.IP, "POST"),
            new Rule("payment-init", "/api/v1/client/checkout", Scope.USER, "POST"),
            new Rule("payment-init", "/api/v1/client/donations", Scope.USER, "POST"),
            new Rule("authenticated-api", "/api/v1/client", Scope.USER, null),
            new Rule("authenticated-api", "/api/v1/partner", Scope.USER, null),
            new Rule("authenticated-api", "/api/v1/talent", Scope.USER, null),
            new Rule("authenticated-api", "/api/v1/cms", Scope.USER, null),
            new Rule("authenticated-api", "/api/v1/ops", Scope.USER, null),
            new Rule("authenticated-api", "/api/v1/admin", Scope.USER, null),
            new Rule("public-api", "/api/v1/public", Scope.IP, null),
            new Rule("public-api", "/api/public", Scope.IP, null)
    );

    public Resolved resolve(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (!path.startsWith(rule.pathPrefix())) {
                continue;
            }
            if (rule.method() != null && !rule.method().equals(method)) {
                continue;
            }
            if (rule.method() != null || !"GET".equals(method) || path.startsWith("/api/")) {
                return new Resolved(rule.policy(), rule.scope());
            }
        }
        return null;
    }
}
