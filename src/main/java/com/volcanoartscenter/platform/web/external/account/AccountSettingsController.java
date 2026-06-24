package com.volcanoartscenter.platform.web.external.account;

import com.volcanoartscenter.platform.security.TotpService;
import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.repository.UserRepository;
import dev.samstevens.totp.exceptions.QrGenerationException;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Controller
public class AccountSettingsController {

    private static final String SESSION_2FA_SETUP_SECRET = "TOTP_SETUP_SECRET";
    private static final long PROFILE_IMAGE_MAX_BYTES = 5L * 1024L * 1024L;
    private static final Map<String, String> PROFILE_IMAGE_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    );
    private static final Set<String> ALLOWED_PROFILE_IMAGE_TYPES = Set.copyOf(PROFILE_IMAGE_TYPES.values());

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;

    @Value("${platform.storage.local-upload-dir:${user.home}/.volcano-platform/uploads}")
    private String localUploadDir;

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

    @PostMapping("/account/settings/profile-image")
    public String updateProfileImage(Authentication authentication,
                                     @RequestParam("profileImage") MultipartFile profileImage,
                                     RedirectAttributes ra) {
        User user = currentUser(authentication);
        if (user == null) return "redirect:/login";
        if (profileImage == null || profileImage.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Choose a profile image to upload.");
            return "redirect:/account/settings";
        }
        if (profileImage.getSize() > PROFILE_IMAGE_MAX_BYTES) {
            ra.addFlashAttribute("errorMessage", "Profile images must be 5 MB or smaller.");
            return "redirect:/account/settings";
        }

        String originalName = profileImage.getOriginalFilename() == null
                ? "" : Path.of(profileImage.getOriginalFilename()).getFileName().toString();
        int extensionIndex = originalName.lastIndexOf('.');
        String extension = extensionIndex >= 0
                ? originalName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT) : "";
        String contentType = profileImage.getContentType() == null
                ? "" : profileImage.getContentType().toLowerCase(Locale.ROOT);
        if (!PROFILE_IMAGE_TYPES.containsKey(extension)
                || !ALLOWED_PROFILE_IMAGE_TYPES.contains(contentType)
                || !PROFILE_IMAGE_TYPES.get(extension).equals(contentType)) {
            ra.addFlashAttribute("errorMessage", "Use a JPG, JPEG, PNG, or WebP profile image.");
            return "redirect:/account/settings";
        }

        try {
            if (!hasValidImageSignature(profileImage, extension)) {
                ra.addFlashAttribute("errorMessage", "The selected file is not a valid supported image.");
                return "redirect:/account/settings";
            }
        } catch (IOException ex) {
            ra.addFlashAttribute("errorMessage", "Could not read the selected profile image.");
            return "redirect:/account/settings";
        }

        try {
            Path uploadRoot = Path.of(localUploadDir).toAbsolutePath().normalize();
            Path profileRoot = uploadRoot.resolve("profiles").normalize();
            Files.createDirectories(profileRoot);
            String storageName = "profile-" + user.getId() + "-" + UUID.randomUUID() + "." + extension;
            Path target = profileRoot.resolve(storageName).normalize();
            if (!target.startsWith(profileRoot)) {
                throw new IOException("Invalid profile image destination.");
            }
            profileImage.transferTo(target);
            user.setProfileImageUrl("/uploads/profiles/" + storageName);
            userRepository.save(user);
            ra.addFlashAttribute("successMessage", "Profile image updated.");
        } catch (IOException ex) {
            ra.addFlashAttribute("errorMessage", "Could not save the profile image. Try again.");
        }
        return "redirect:/account/settings";
    }

    private boolean hasValidImageSignature(MultipartFile file, String extension) throws IOException {
        byte[] header;
        try (var input = file.getInputStream()) {
            header = input.readNBytes(12);
        }
        return switch (extension) {
            case "jpg", "jpeg" -> header.length >= 3
                    && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff;
            case "png" -> header.length >= 8
                    && (header[0] & 0xff) == 0x89 && header[1] == 0x50 && header[2] == 0x4e
                    && header[3] == 0x47 && header[4] == 0x0d && header[5] == 0x0a
                    && header[6] == 0x1a && header[7] == 0x0a;
            case "webp" -> header.length >= 12
                    && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
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
