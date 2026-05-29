package com.volcanoartscenter.platform.shared.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "blog_posts")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class BlogPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, unique = true, length = 350)
    private String slug;

    @Column(name = "excerpt", length = 500)
    private String excerpt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Featured image
    @Column(name = "featured_image_url")
    private String featuredImageUrl;

    @Column(name = "cover_media_id")
    private Long coverMediaId;

    @ElementCollection
    @CollectionTable(name = "blog_post_images", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "image_url")
    @Builder.Default
    private List<String> additionalImages = new ArrayList<>();

    // Categorization
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private BlogCategory category = BlogCategory.UPDATE;

    @ElementCollection
    @CollectionTable(name = "blog_post_tags", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "tag", length = 50)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    // Author
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(name = "author_display_name", length = 150)
    private String authorDisplayName;

    // SEO
    @Column(name = "meta_title", length = 200)
    private String metaTitle;

    @Column(name = "meta_description", length = 400)
    private String metaDescription;

    // Publishing
    @Column(nullable = false)
    @Builder.Default
    private Boolean published = false;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // Engagement
    @Column(name = "view_count")
    @Builder.Default
    private Long viewCount = 0L;

    @Column(name = "is_highlighted", columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean highlighted = false;

    // Timestamps
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum BlogCategory {
        UPDATE,
        EVENT,
        STORY,
        CULTURE,
        CONSERVATION,
        TESTIMONIAL
    }

    @Transient
    public String getAdditionalImagesText() {
        List<String> images = additionalImages == null ? java.util.Collections.emptyList() : additionalImages;
        return String.join(System.lineSeparator(), images);
    }

    @Transient
    public String getDisplayFeaturedImageUrl() {
        if (featuredImageUrl == null) {
            return null;
        }
        String normalized = featuredImageUrl.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("logo") || lower.contains("placeholder") || lower.contains("default")) {
            return null;
        }
        return normalized;
    }
}
