package com.volcanoartscenter.platform.security;

import com.volcanoartscenter.platform.security.oauth.GoogleOAuth2UserService;
import com.volcanoartscenter.platform.web.external.account.TwoFactorLoginController;
import com.volcanoartscenter.platform.shared.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Stateless JWT chain — runs first and only matches /api/v1/partner/**.
     * Uses Spring Security's OAuth2 resource server JWT support for token
     * validation (RS256, verified against the partner JWK).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain partnerApiFilterChain(HttpSecurity http, JwtDecoder partnerJwtDecoder) throws Exception {
        http
            .securityMatcher("/api/v1/partner/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/partner/auth/**").permitAll()
                .requestMatchers("/api/v1/partner/**").hasAnyRole("TOUR_OPERATOR", "OPS_MANAGER", "SUPER_ADMIN")
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                    .decoder(partnerJwtDecoder)
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
            ));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http,
                                              ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository,
                                              GoogleOAuth2UserService googleOAuth2UserService,
                                              AuthenticationSuccessHandler roleBasedSuccessHandler) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public pages — accessible by everyone (guest browsing + cart pre-checkout)
                .requestMatchers(
                    "/", "/about", "/departments/**",
                    "/art-store/**", "/experiences/**",
                    "/conservation/**", "/conservation",
                    "/talent", "/talent/apply",
                    "/blog/**", "/contact",
                    "/error",
                    "/register", "/forgot-password", "/login/verify-2fa",
                    "/oauth2/**", "/login/oauth2/**",
                    "/tour-operators/request", "/tour-operators/register",
                    "/talent/register",
                    "/cart", "/cart/add", "/cart/remove",
                    "/css/**", "/js/**", "/images/**", "/fonts/**", "/uploads/**",
                    "/api/public/**", "/api/v1/public/**", "/api/v1/webhooks/**",
                    "/actuator/health/**", "/actuator/info"
                ).permitAll()

                // Actuator: everything else (metrics, prometheus, env, beans, ...) restricted
                .requestMatchers("/actuator/**").hasRole("SUPER_ADMIN")

                // Internal role-based access (PRD §3 — 7 canonical roles)
                .requestMatchers("/admin/dashboard", "/admin/users/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/admin/content/**").hasAnyRole("CONTENT_MANAGER", "SUPER_ADMIN")
                .requestMatchers("/admin/ops/**").hasAnyRole("OPS_MANAGER", "SUPER_ADMIN")
                .requestMatchers("/admin/messages/**").hasAnyRole("CONTENT_MANAGER", "OPS_MANAGER", "SUPER_ADMIN")
                .requestMatchers("/api/v1/admin/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/v1/cms/**").hasAnyRole("CONTENT_MANAGER", "SUPER_ADMIN")
                .requestMatchers("/api/v1/ops/**").hasAnyRole("OPS_MANAGER", "SUPER_ADMIN")
                .requestMatchers("/tour-operators/portal/**").hasRole("TOUR_OPERATOR")
                .requestMatchers("/talent/dashboard/**").hasRole("TALENT_APPLICANT")
                .requestMatchers("/api/v1/talent/**").hasAnyRole("TALENT_APPLICANT", "OPS_MANAGER", "SUPER_ADMIN")
                .requestMatchers("/client/**").hasRole("REGISTERED_CLIENT")
                .requestMatchers("/api/v1/client/**").hasAnyRole("REGISTERED_CLIENT", "OPS_MANAGER", "SUPER_ADMIN")

                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(roleBasedSuccessHandler)
                .usernameParameter("username")
                .permitAll()
            );

        // Enable OAuth login only when the Google registration exists.
        if (hasGoogleRegistration(clientRegistrationRepository.getIfAvailable())) {
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .userInfoEndpoint(userInfo -> userInfo.userService(googleOAuth2UserService))
                    .successHandler(roleBasedSuccessHandler));
        }

        http
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?logged_out")
                .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
            )
            .headers(h -> h
                .contentTypeOptions(c -> {})
                .frameOptions(f -> f.sameOrigin())
                .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                .permissionsPolicyHeader(p -> p.policy("geolocation=(), microphone=(), camera=()"))
            );

        return http.build();
    }

    /** Reads {@code roles} claim ("ROLE_TOUR_OPERATOR", ...) into Spring authorities. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByEmail(username)
                .map(user -> User.withUsername(user.getEmail())
                        .password(user.getPassword())
                        .disabled(!Boolean.TRUE.equals(user.getEnabled()))
                        .authorities(
                                user.getRoles().stream()
                                        .map(role -> "ROLE_" + role.getName())
                                        .toArray(String[]::new)
                        )
                        .build()
                )
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public AuthenticationSuccessHandler roleBasedSuccessHandler(UserRepository userRepository) {
        return (request, response, authentication) -> {
            var userOpt = userRepository.findByEmail(authentication.getName());
            if (userOpt.isPresent() && Boolean.TRUE.equals(userOpt.get().getTwoFactorEnabled())
                    && userOpt.get().getTotpSecret() != null) {
                HttpSession session = request.getSession(true);
                session.setAttribute(TwoFactorLoginController.SESSION_PENDING_EMAIL, authentication.getName());
                String redirect = request.getParameter("redirect");
                if (isSafeRedirect(redirect)) {
                    session.setAttribute(TwoFactorLoginController.SESSION_PENDING_REDIRECT, redirect);
                } else {
                    HttpSession existing = request.getSession(false);
                    if (existing != null) {
                        Object sessionRedirect = existing.getAttribute("postLoginRedirect");
                        if (sessionRedirect instanceof String value && isSafeRedirect(value)) {
                            session.setAttribute(TwoFactorLoginController.SESSION_PENDING_REDIRECT, value);
                        }
                        existing.removeAttribute("postLoginRedirect");
                    }
                }
                SecurityContextHolder.clearContext();
                session.removeAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                response.sendRedirect("/login/verify-2fa");
                return;
            }

            boolean isSuperAdmin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
            boolean isContentManager = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_CONTENT_MANAGER".equals(a.getAuthority()));
            boolean isOpsManager = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_OPS_MANAGER".equals(a.getAuthority()));
            boolean isTourOperator = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_TOUR_OPERATOR".equals(a.getAuthority()));
            boolean isTalentApplicant = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_TALENT_APPLICANT".equals(a.getAuthority()));
            if (isSuperAdmin) {
                response.sendRedirect("/admin/dashboard");
            } else if (isContentManager) {
                response.sendRedirect("/admin/content/dashboard");
            } else if (isOpsManager) {
                response.sendRedirect("/admin/ops/dashboard");
            } else if (isTourOperator) {
                response.sendRedirect("/tour-operators/portal");
            } else if (isTalentApplicant) {
                response.sendRedirect("/talent/dashboard");
            } else if (authentication.getAuthorities().stream().anyMatch(a -> "ROLE_REGISTERED_CLIENT".equals(a.getAuthority()))) {
                String redirect = request.getParameter("redirect");
                if (!isSafeRedirect(redirect)) {
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        Object sessionRedirect = session.getAttribute("postLoginRedirect");
                        if (sessionRedirect instanceof String value) {
                            redirect = value;
                        }
                        session.removeAttribute("postLoginRedirect");
                    }
                }
                if (isSafeRedirect(redirect)) {
                    response.sendRedirect(redirect);
                } else {
                    response.sendRedirect("/client/dashboard");
                }
            } else {
                response.sendRedirect("/");
            }
        };
    }

    private static boolean isSafeRedirect(String redirect) {
        return redirect != null && redirect.startsWith("/") && !redirect.startsWith("//");
    }

    private static boolean hasGoogleRegistration(ClientRegistrationRepository repository) {
        return repository != null && repository.findByRegistrationId("google") != null;
    }
}
