package com.volcanoartscenter.platform.web.external.account;

import com.volcanoartscenter.platform.security.TotpService;
import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.stream.Collectors;

/**
 * Completes login when a user with 2FA enabled has passed password auth
 * but still needs to enter their authenticator code.
 */
@Controller
public class TwoFactorLoginController {

    public static final String SESSION_PENDING_EMAIL = "PENDING_2FA_EMAIL";
    public static final String SESSION_PENDING_REDIRECT = "PENDING_2FA_REDIRECT";

    private final UserRepository userRepository;
    private final UserDetailsService userDetailsService;
    private final TotpService totpService;

    public TwoFactorLoginController(UserRepository userRepository,
                                    UserDetailsService userDetailsService,
                                    TotpService totpService) {
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
        this.totpService = totpService;
    }

    @GetMapping("/login/verify-2fa")
    public String verifyPage(HttpSession session, Model model) {
        if (session.getAttribute(SESSION_PENDING_EMAIL) == null) {
            return "redirect:/login";
        }
        model.addAttribute("currentPage", "login");
        model.addAttribute("pageTitle", "Two-Factor Verification — Volcano Arts Center");
        return "external/guest/verify-2fa";
    }

    @PostMapping("/login/verify-2fa")
    public String verifyCode(@RequestParam String code,
                             HttpSession session,
                             RedirectAttributes ra) {
        String email = (String) session.getAttribute(SESSION_PENDING_EMAIL);
        if (email == null) {
            return "redirect:/login";
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getTwoFactorEnabled()) || user.getTotpSecret() == null) {
            session.removeAttribute(SESSION_PENDING_EMAIL);
            return "redirect:/login";
        }
        if (!totpService.verify(user.getTotpSecret(), code)) {
            ra.addFlashAttribute("errorMessage", "Invalid code. Check your authenticator app and try again.");
            return "redirect:/login/verify-2fa";
        }

        UserDetails details = userDetailsService.loadUserByUsername(email);
        var authorities = details.getAuthorities().stream()
                .map(a -> new SimpleGrantedAuthority(a.getAuthority()))
                .collect(Collectors.toList());
        var auth = new UsernamePasswordAuthenticationToken(details.getUsername(), details.getPassword(), authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        String redirect = (String) session.getAttribute(SESSION_PENDING_REDIRECT);
        session.removeAttribute(SESSION_PENDING_EMAIL);
        session.removeAttribute(SESSION_PENDING_REDIRECT);

        return "redirect:" + resolveDashboardRedirect(user, redirect);
    }

    private String resolveDashboardRedirect(User user, String redirect) {
        if (redirect != null && redirect.startsWith("/") && !redirect.startsWith("//")) {
            return redirect;
        }
        return user.getRoles().stream()
                .map(r -> r.getName())
                .findFirst()
                .map(role -> switch (role) {
                    case "SUPER_ADMIN" -> "/admin/dashboard";
                    case "CONTENT_MANAGER" -> "/admin/content/dashboard";
                    case "OPS_MANAGER" -> "/admin/ops/dashboard";
                    case "TOUR_OPERATOR" -> "/tour-operators/portal";
                    case "TALENT_APPLICANT" -> "/talent/dashboard";
                    case "REGISTERED_CLIENT" -> "/client/dashboard";
                    default -> "/";
                })
                .orElse("/");
    }
}
