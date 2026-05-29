package com.volcanoartscenter.platform.web.external.account;

import com.volcanoartscenter.platform.security.TotpService;
import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.repository.UserRepository;
import dev.samstevens.totp.exceptions.QrGenerationException;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountSettingsController {

    private static final String SESSION_2FA_SETUP_SECRET = "TOTP_SETUP_SECRET";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;

    public AccountSettingsController(UserRepository userRepository,
                                     PasswordEncoder passwordEncoder,
                                     TotpService totpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
    }

    @GetMapping("/account/settings")
    public String settings(Authentication authentication, HttpSession session, Model model) {
        User user = currentUser(authentication);
        if (user == null) return "redirect:/login";
        model.addAttribute("currentPage", "account-settings");
        model.addAttribute("pageTitle", "Account Settings — Volcano Arts Center");
        model.addAttribute("account", user);
        model.addAttribute("twoFactorEnabled", Boolean.TRUE.equals(user.getTwoFactorEnabled()));

        String setupSecret = (String) session.getAttribute(SESSION_2FA_SETUP_SECRET);
        if (setupSecret != null && !Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            model.addAttribute("setupSecret", setupSecret);
            try {
                model.addAttribute("qrCodeUri", totpService.qrCodeDataUri(user, setupSecret));
            } catch (QrGenerationException e) {
                model.addAttribute("errorMessage", "Could not generate QR code. Try again.");
            }
        }
        return "external/account/settings";
    }

    @PostMapping("/account/settings/profile")
    public String updateProfile(Authentication authentication,
                                @RequestParam String firstName,
                                @RequestParam String lastName,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String country,
                                RedirectAttributes ra) {
        User user = currentUser(authentication);
        if (user == null) return "redirect:/login";
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            ra.addFlashAttribute("errorMessage", "First and last name are required.");
            return "redirect:/account/settings";
        }
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setPhone(phone != null ? phone.trim() : null);
        user.setCountry(country != null ? country.trim() : null);
        userRepository.save(user);
        ra.addFlashAttribute("successMessage", "Profile updated successfully.");
        return "redirect:/account/settings";
    }

    @PostMapping("/account/settings/email")
    public String updateEmail(Authentication authentication,
                              @RequestParam String email,
                              @RequestParam String currentPassword,
                              RedirectAttributes ra) {
        User user = currentUser(authentication);
        if (user == null) return "redirect:/login";

        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isBlank() || !normalized.contains("@")) {
            ra.addFlashAttribute("errorMessage", "Please enter a valid email address.");
            return "redirect:/account/settings";
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            ra.addFlashAttribute("errorMessage", "Your current password is incorrect.");
            return "redirect:/account/settings";
        }
        if (normalized.equals(user.getEmail())) {
            ra.addFlashAttribute("successMessage", "That is already your email address.");
            return "redirect:/account/settings";
        }
        if (userRepository.findByEmail(normalized).filter(u -> !u.getId().equals(user.getId())).isPresent()) {
            ra.addFlashAttribute("errorMessage", "That email address is already in use.");
            return "redirect:/account/settings";
        }
        user.setEmail(normalized);
        userRepository.save(user);
        ra.addFlashAttribute("successMessage", "Email updated. Please sign in again with your new email.");
        return "redirect:/login";
    }

    @PostMapping("/account/settings/password")
    public String updatePassword(Authentication authentication,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes ra) {
        User user = currentUser(authentication);
        if (user == null) return "redirect:/login";

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            ra.addFlashAttribute("errorMessage", "Your current password is incorrect.");
            return "redirect:/account/settings";
        }
        if (newPassword == null || newPassword.length() < 8) {
            ra.addFlashAttribute("errorMessage", "New password must be at least 8 characters.");
            return "redirect:/account/settings";
        }
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("errorMessage", "New password and confirmation do not match.");
            return "redirect:/account/settings";
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        ra.addFlashAttribute("successMessage", "Password changed successfully.");
        return "redirect:/account/settings";
    }

    @PostMapping("/account/settings/2fa/begin")
    public String beginTwoFactor(Authentication authentication, HttpSession session, RedirectAttributes ra) {
        User user = currentUser(authentication);
        if (user == null) return "redirect:/login";
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            ra.addFlashAttribute("errorMessage", "Two-factor authentication is already enabled.");
            return "redirect:/account/settings";
        }
        session.setAttribute(SESSION_2FA_SETUP_SECRET, totpService.generateSecret());
        return "redirect:/account/settings";
    }

    @PostMapping("/account/settings/2fa/enable")
    public String enableTwoFactor(Authentication authentication,
                                  HttpSession session,
                                  @RequestParam String code,
                                  RedirectAttributes ra) {
        User user = currentUser(authentication);
        if (user == null) return "redirect:/login";
        String secret = (String) session.getAttribute(SESSION_2FA_SETUP_SECRET);
        if (secret == null) {
            ra.addFlashAttribute("errorMessage", "Start setup again before enabling 2FA.");
            return "redirect:/account/settings";
        }
        if (!totpService.verify(secret, code)) {
            ra.addFlashAttribute("errorMessage", "Invalid code. Scan the QR code and enter the 6-digit code from your app.");
            return "redirect:/account/settings";
        }
        user.setTotpSecret(secret);
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
        session.removeAttribute(SESSION_2FA_SETUP_SECRET);
        ra.addFlashAttribute("successMessage", "Two-factor authentication is now enabled on your account.");
        return "redirect:/account/settings";
    }

    @PostMapping("/account/settings/2fa/disable")
    public String disableTwoFactor(Authentication authentication,
                                   @RequestParam String currentPassword,
                                   @RequestParam String code,
                                   RedirectAttributes ra) {
        User user = currentUser(authentication);
        if (user == null) return "redirect:/login";
        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            return "redirect:/account/settings";
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            ra.addFlashAttribute("errorMessage", "Your current password is incorrect.");
            return "redirect:/account/settings";
        }
        if (!totpService.verify(user.getTotpSecret(), code)) {
            ra.addFlashAttribute("errorMessage", "Invalid authenticator code.");
            return "redirect:/account/settings";
        }
        user.setTwoFactorEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
        ra.addFlashAttribute("successMessage", "Two-factor authentication has been disabled.");
        return "redirect:/account/settings";
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }
}
