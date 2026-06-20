package com.volcanoartscenter.platform.web.external.talentapplicant.controller;

import com.volcanoartscenter.platform.shared.model.TalentApplication;
import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.service.CaptchaService;
import com.volcanoartscenter.platform.web.external.talentapplicant.service.TalentApplicantService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class TalentApplicantController {

    private final TalentApplicantService talentApplicantService;
    private final CaptchaService captchaService;
    private final com.volcanoartscenter.platform.shared.repository.MediaAssetRepository mediaAssetRepository;

    @org.springframework.beans.factory.annotation.Value("${platform.storage.local-upload-dir:${user.home}/.volcano-platform/uploads}")
    private String localUploadDir;

    @GetMapping("/talent/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        User user = currentUser(authentication).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }
        var apps = talentApplicantService.applicationsFor(user);
        var latest = talentApplicantService.latestFor(user).orElse(null);

        model.addAttribute("currentPage", "talent-dashboard");
        model.addAttribute("pageTitle", "Talent Console");
        model.addAttribute("user", user);
        model.addAttribute("applications", apps);
        model.addAttribute("latestApplication", latest);
        model.addAttribute("categories", TalentApplication.ApplicantCategory.values());
        model.addAttribute("areas", TalentApplication.TalentArea.values());

        // Portfolio data
        model.addAttribute("portfolioItems", mediaAssetRepository.searchForCms(user.getEmail(), null));

        return "external/talent-applicant/dashboard";
    }

    @PostMapping("/talent/dashboard/portfolio/upload")
    public String uploadPortfolioMedia(Authentication authentication,
                                       @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                       @RequestParam(required = false) String title,
                                       RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication).orElse(null);
        if (user == null) return "redirect:/login";

        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a file to upload.");
            return "redirect:/talent/dashboard#portfolio";
        }

        try {
            java.nio.file.Path uploadRoot = java.nio.file.Path.of(localUploadDir).toAbsolutePath().normalize();
            java.nio.file.Files.createDirectories(uploadRoot);

            String originalName = file.getOriginalFilename();
            String ext = "";
            if (originalName != null && originalName.lastIndexOf('.') >= 0) {
                ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase(java.util.Locale.ROOT);
            }

            String storageKey = java.util.UUID.randomUUID().toString() + ext;
            java.nio.file.Path target = uploadRoot.resolve(storageKey).normalize();
            java.nio.file.Files.copy(file.getInputStream(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            com.volcanoartscenter.platform.shared.model.MediaAsset asset = com.volcanoartscenter.platform.shared.model.MediaAsset.builder()
                    .storageKey(storageKey)
                    .publicUrl("/uploads/" + storageKey)
                    .contentType(file.getContentType())
                    .title(title != null && !title.isBlank() ? title : "Portfolio: " + originalName)
                    .altText("Portfolio piece for " + user.getFullName())
                    .fileSizeBytes(file.getSize())
                    // We tag the asset with user email in title or alt text for searching if we don't have a direct link
                    .altText("OWNER:" + user.getEmail())
                    .build();

            mediaAssetRepository.save(asset);
            redirectAttributes.addFlashAttribute("successMessage", "Portfolio piece uploaded successfully.");
        } catch (java.io.IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Upload failed: " + e.getMessage());
        }

        return "redirect:/talent/dashboard#portfolio";
    }


    @GetMapping("/talent/register")
    public String registerPage(Model model) {
        model.addAttribute("currentPage", "talent");
        model.addAttribute("pageTitle", "Talent Applicant Registration");
        return "external/talent-applicant/register";
    }

    @PostMapping("/talent/register")
    public String register(@RequestParam String firstName,
                           @RequestParam String lastName,
                           @RequestParam String email,
                           @RequestParam(required = false) String phone,
                           @RequestParam(required = false) String country,
                           @RequestParam String password,
                           RedirectAttributes redirectAttributes) {
        try {
            talentApplicantService.registerApplicantAccount(firstName, lastName, email, phone, country, password);
            redirectAttributes.addFlashAttribute("successMessage", "Talent applicant account created. Please sign in.");
            return "redirect:/login";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
            return "redirect:/talent/register";
        }
    }

    @PostMapping("/talent/dashboard/apply")
    public String apply(Authentication authentication,
                        @RequestParam String fullName,
                        @RequestParam(required = false) String email,
                        @RequestParam(required = false) String phone,
                        @RequestParam(required = false) String ageRange,
                        @RequestParam(required = false) String gender,
                        @RequestParam(required = false) String location,
                        @RequestParam TalentApplication.ApplicantCategory applicantCategory,
                        @RequestParam TalentApplication.TalentArea talentArea,
                        @RequestParam(required = false) String experienceDescription,
                        @RequestParam(required = false) String motivation,
                        @RequestParam(required = false) String availabilityDetails,
                        @RequestParam(required = false) String accessibilityNeeds,
                        @RequestParam(defaultValue = "EMAIL") String preferredContactChannel,
                        @RequestParam(required = false) String captchaToken,
                        @RequestParam(required = false) java.util.List<org.springframework.web.multipart.MultipartFile> portfolioImages,
                        @RequestParam(required = false) java.util.List<org.springframework.web.multipart.MultipartFile> performanceVideos,
                        @RequestParam(required = false) java.util.List<org.springframework.web.multipart.MultipartFile> artworkSamples,
                        RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }
        if (!captchaService.verify(captchaToken)) {
            redirectAttributes.addFlashAttribute("successMessage", "Captcha validation failed.");
            return "redirect:/talent/dashboard";
        }
        talentApplicantService.createApplication(user, fullName, email, phone, ageRange, gender, location, applicantCategory, talentArea,
                experienceDescription, motivation, availabilityDetails, accessibilityNeeds, preferredContactChannel);
                
        // Handle media uploads if provided
        try {
            java.nio.file.Path uploadRoot = java.nio.file.Path.of(localUploadDir).toAbsolutePath().normalize();
            java.nio.file.Files.createDirectories(uploadRoot);
            
            processUploads(portfolioImages, "Portfolio Image", user, uploadRoot);
            processUploads(performanceVideos, "Performance Video", user, uploadRoot);
            processUploads(artworkSamples, "Artwork Sample", user, uploadRoot);
        } catch (Exception e) {
            // Log error, but don't fail the application submission
            System.err.println("Failed to process some media uploads: " + e.getMessage());
        }
                
        redirectAttributes.addFlashAttribute("successMessage", "Application submitted successfully.");
        return "redirect:/talent/dashboard";
    }

    private void processUploads(java.util.List<org.springframework.web.multipart.MultipartFile> files, String prefix, User user, java.nio.file.Path uploadRoot) throws java.io.IOException {
        if (files == null) return;
        for (org.springframework.web.multipart.MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String originalName = file.getOriginalFilename();
                String ext = "";
                if (originalName != null && originalName.lastIndexOf('.') >= 0) {
                    ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase(java.util.Locale.ROOT);
                }

                String storageKey = java.util.UUID.randomUUID().toString() + ext;
                java.nio.file.Path target = uploadRoot.resolve(storageKey).normalize();
                java.nio.file.Files.copy(file.getInputStream(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                com.volcanoartscenter.platform.shared.model.MediaAsset asset = com.volcanoartscenter.platform.shared.model.MediaAsset.builder()
                        .storageKey(storageKey)
                        .publicUrl("/uploads/" + storageKey)
                        .contentType(file.getContentType())
                        .title(prefix + " - Submission")
                        .altText("OWNER:" + user.getEmail())
                        .fileSizeBytes(file.getSize())
                        .build();

                mediaAssetRepository.save(asset);
            }
        }
    }

    @PostMapping("/talent/dashboard/applications/{id}/update")
    public String updateApplication(Authentication authentication,
                                    @PathVariable Long id,
                                    @RequestParam(required = false) String ageRange,
                                    @RequestParam(required = false) String gender,
                                    @RequestParam(required = false) String location,
                                    @RequestParam(required = false) String experienceDescription,
                                    @RequestParam(required = false) String motivation,
                                    @RequestParam(required = false) String availabilityDetails,
                                    @RequestParam(required = false) String accessibilityNeeds,
                                    @RequestParam(defaultValue = "EMAIL") String preferredContactChannel,
                                    RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            talentApplicantService.updateOwnApplication(user, id, ageRange, gender, location, experienceDescription, motivation,
                    availabilityDetails, accessibilityNeeds, preferredContactChannel);
            redirectAttributes.addFlashAttribute("successMessage", "Application updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/talent/dashboard";
    }

    @PostMapping("/talent/dashboard/profile")
    public String updateProfile(Authentication authentication,
                                @RequestParam String firstName,
                                @RequestParam String lastName,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String country,
                                RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhone(phone);
        user.setCountry(country);
        talentApplicantService.saveProfile(user);
        redirectAttributes.addFlashAttribute("successMessage", "Profile updated.");
        return "redirect:/talent/dashboard";
    }

    private Optional<User> currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return Optional.empty();
        }
        return talentApplicantService.findUserByEmail(authentication.getName());
    }
}
