package com.volcanoartscenter.platform.shared.repository;

import com.volcanoartscenter.platform.shared.model.Review;
import com.volcanoartscenter.platform.shared.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findTop6ByApprovedTrueOrderByCreatedAtDesc();
    List<Review> findByProductIdAndApprovedTrueOrderByCreatedAtDesc(Long productId);
    List<Review> findByExperienceIdAndApprovedTrueOrderByCreatedAtDesc(Long experienceId);

    @EntityGraph(attributePaths = {"product", "experience", "user"})
    List<Review> findTop200ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"product", "experience"})
    List<Review> findByUserOrderByCreatedAtDesc(User user);

    Optional<Review> findByUserAndProductId(User user, Long productId);
    Optional<Review> findByUserAndExperienceId(User user, Long experienceId);
    long countByApprovedFalse();
}
