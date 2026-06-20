package com.volcanoartscenter.platform.shared.repository;

import com.volcanoartscenter.platform.shared.model.BlogPost;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {
    @EntityGraph(attributePaths = {"additionalImages", "tags"})
    List<BlogPost> findByPublishedTrueOrderByPublishedAtDesc();

    @EntityGraph(attributePaths = {"additionalImages", "tags"})
    Optional<BlogPost> findBySlugAndPublishedTrue(String slug);

    @EntityGraph(attributePaths = {"additionalImages", "tags"})
    Optional<BlogPost> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** Used by BlogSchedulerService to auto-publish posts at their scheduled time. */
    List<BlogPost> findByPublishedFalseAndPublishedAtNotNullAndPublishedAtBefore(LocalDateTime cutoff);

    @EntityGraph(attributePaths = {"additionalImages", "tags"})
    @Query("""
            SELECT p FROM BlogPost p
            WHERE (:q IS NULL OR :q = '' OR
                   LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) OR
                   LOWER(COALESCE(p.excerpt, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR
                   LOWER(p.content) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:category IS NULL OR p.category = :category)
            ORDER BY p.published DESC, p.publishedAt DESC, p.createdAt DESC
            """)
    List<BlogPost> searchForCms(@Param("q") String q, @Param("category") BlogPost.BlogCategory category);
}
