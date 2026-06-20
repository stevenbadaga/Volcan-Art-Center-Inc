package com.volcanoartscenter.platform.web.external.guest.controller;

import com.volcanoartscenter.platform.shared.model.Review;
import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.repository.UserRepository;
import com.volcanoartscenter.platform.shared.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class PublicReviewController {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    @PostMapping("/reviews/submit")
    public String submitReview(@RequestParam Integer rating,
                               @RequestParam String comment,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        
        if (authentication == null || !authentication.isAuthenticated()) {
            redirectAttributes.addFlashAttribute("errorMessage", "You must be signed in to submit a review.");
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        Review review = Review.builder()
                .reviewerName(user.getFirstName() + " " + user.getLastName())
                .reviewerEmail(user.getEmail())
                .reviewerCountry(user.getCountry())
                .user(user)
                .rating(rating)
                .comment(comment)
                .approved(false) // Needs admin approval
                .featured(false)
                .build();

        reviewRepository.save(review);

        redirectAttributes.addFlashAttribute("successMessage", "Thank you! Your review has been submitted and is pending admin approval.");
        return "redirect:/#testimonials";
    }
}
