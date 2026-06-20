package com.volcanoartscenter.platform.web.external.guest.controller;

import com.volcanoartscenter.platform.shared.model.BlogPost;
import com.volcanoartscenter.platform.shared.model.DonationCampaign;
import com.volcanoartscenter.platform.shared.model.Experience;
import com.volcanoartscenter.platform.shared.model.Product;
import com.volcanoartscenter.platform.shared.repository.BlogPostRepository;
import com.volcanoartscenter.platform.shared.repository.DonationCampaignRepository;
import com.volcanoartscenter.platform.shared.repository.ExperienceRepository;
import com.volcanoartscenter.platform.shared.repository.ProductRepository;
import com.volcanoartscenter.platform.shared.repository.ReviewRepository;
import com.volcanoartscenter.platform.shared.repository.TalentProfileRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class GuestController {

    private final ProductRepository productRepository;
    private final ExperienceRepository experienceRepository;
    private final BlogPostRepository blogPostRepository;
    private final DonationCampaignRepository donationCampaignRepository;
    private final ReviewRepository reviewRepository;
    private final TalentProfileRepository talentProfileRepository;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository;

    @GetMapping("/")
    public String home(Model model) {
        List<Product> featuredArtworks = productRepository
                .findTop8ByAvailableTrueAndArtworkStatusOrderByFeaturedDescCreatedAtDesc(Product.ArtworkStatus.PUBLISHED);

        List<Experience> featuredExperiences = experienceRepository.findByActiveTrueOrderByFeaturedDescTitleAsc()
                .stream()
                .filter(e -> Boolean.TRUE.equals(e.getActive()))
                .limit(8)
                .toList();

        List<BlogPost> latestPosts = blogPostRepository.findByPublishedTrueOrderByPublishedAtDesc()
                .stream()
                .limit(3)
                .toList();

        List<DonationCampaign> activeCampaigns = donationCampaignRepository.findByActiveTrueOrderByNameAsc();
        DonationCampaign featuredCampaign = activeCampaigns.isEmpty() ? null : activeCampaigns.get(0);

        var topReviews = reviewRepository.findAll()
                .stream()
                .filter(r -> Boolean.TRUE.equals(r.getApproved()))
                .filter(r -> r.getRating() != null && r.getRating() >= 4)
                .sorted(Comparator.comparing(r -> r.getCreatedAt() == null ? java.time.LocalDateTime.MIN : r.getCreatedAt(),
                        Comparator.reverseOrder()))
                .limit(4)
                .toList();

        model.addAttribute("currentPage", "home");
        model.addAttribute("pageTitle", "Volcano Arts Center — Authentic Rwandan Art, Culture & Community Tourism");
        model.addAttribute("metaDescription",
                "Discover and buy original Rwandan artworks, book authentic cultural experiences, and support conservation. "
                + "Volcano Arts Center Inc — Musanze, Rwanda.");
        model.addAttribute("featuredArtworks", featuredArtworks);
        model.addAttribute("featuredExperiences", featuredExperiences);
        model.addAttribute("latestPosts", latestPosts);
        model.addAttribute("featuredCampaign", featuredCampaign);
        model.addAttribute("activeCampaigns", activeCampaigns);
        model.addAttribute("topReviews", topReviews);
        model.addAttribute("talentProfiles", talentProfileRepository.findByPublishedTrueOrderByIdDesc()
                .stream()
                .limit(3)
                .toList());
        return "external/guest/home";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("currentPage", "about");
        model.addAttribute("pageTitle", "About Volcano Arts Center");
        model.addAttribute("metaDescription",
                "Learn about Volcano Arts Center Inc — empowering communities through art and tourism since 2012 in Musanze, Rwanda.");
        return "external/guest/about";
    }

    @GetMapping("/contact")
    public String contact(Model model) {
        model.addAttribute("currentPage", "contact");
        model.addAttribute("pageTitle", "Contact Volcano Arts Center");
        model.addAttribute("metaDescription",
                "Get in touch with Volcano Arts Center Inc. Inquire about cultural experiences, art purchases, partnerships, or plan your visit near Volcanoes National Park.");
        return "external/guest/contact";
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String redirect, HttpServletRequest request, Model model) {
        model.addAttribute("pageTitle", "Sign In — Volcano Arts Center");
        ClientRegistrationRepository registrations = clientRegistrationRepository.getIfAvailable();
        model.addAttribute("googleOauthEnabled", registrations != null && registrations.findByRegistrationId("google") != null);
        String safeRedirect = null;
        if (redirect != null && redirect.startsWith("/") && !redirect.startsWith("//")) {
            safeRedirect = redirect;
            request.getSession(true).setAttribute("postLoginRedirect", redirect);
        } else if (request.getSession(false) != null) {
            request.getSession(false).removeAttribute("postLoginRedirect");
        }
        model.addAttribute("loginRedirect", safeRedirect);
        return "internal/super-admin/login";
    }
}
