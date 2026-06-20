package com.volcanoartscenter.platform.shared.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "experiences")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Experience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(nullable = false, unique = true, length = 250)
    private String slug;

    // Categorization
    @Enumerated(EnumType.STRING)
    @Column(name = "experience_type", nullable = false, length = 30)
    private ExperienceType experienceType;

    // Pricing
    @Column(name = "price_per_person", precision = 10, scale = 2)
    private BigDecimal pricePerPerson;

    @Column(name = "group_price", precision = 10, scale = 2)
    private BigDecimal groupPrice;

    @Column(name = "price_note", length = 300)
    private String priceNote;

    // Capacity & duration
    @Column(name = "min_group_size")
    @Builder.Default
    private Integer minGroupSize = 1;

    @Column(name = "max_group_size")
    @Builder.Default
    private Integer maxGroupSize = 15;

    @Column(name = "duration_hours", precision = 4, scale = 1)
    private BigDecimal durationHours;

    // Booking type
    @Enumerated(EnumType.STRING)
    @Column(name = "booking_type", nullable = false, length = 20)
    @Builder.Default
    private BookingType bookingType = BookingType.INQUIRY;

    // Location
    @Column(length = 300)
    private String location;

    @Column(name = "meeting_point", length = 300)
    private String meetingPoint;

    // Languages
    @Column(name = "languages_offered", length = 100)
    @Builder.Default
    private String languagesOffered = "English, French";

    // What's included
    @Column(name = "whats_included", columnDefinition = "TEXT")
    private String whatsIncluded;

    @Column(name = "what_to_bring", columnDefinition = "TEXT")
    private String whatToBring;

    // Images
    @Column(name = "primary_image_url")
    private String primaryImageUrl;

    @Column(name = "primary_media_id")
    private Long primaryMediaId;

    @ElementCollection
    @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
    @CollectionTable(name = "experience_images", joinColumns = @JoinColumn(name = "experience_id"))
    @Column(name = "image_url")
    @Builder.Default
    private List<String> additionalImages = new ArrayList<>();

    // Availability
    @Column(name = "available_daily")
    @Builder.Default
    private Boolean availableDaily = true;

    @Column(name = "available_days", length = 100)
    private String availableDays; // e.g., "MON,TUE,WED,THU,FRI,SAT"

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "featured")
    @Builder.Default
    private Boolean featured = false;

    // Reviews
    @OneToMany(mappedBy = "experience", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();

    // Bookings
    @OneToMany(mappedBy = "experience", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Booking> bookings = new ArrayList<>();

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

    public enum ExperienceType {
        CULTURAL,        // Dance, storytelling, art
        VILLAGE,         // Village walks, biking, community life
        CONSERVATION,    // Eco-conscious, tree planting
        CUSTOM           // Customized packages
    }

    public enum BookingType {
        DIRECT,     // Can book directly
        INQUIRY     // Must inquire first
    }

    @Transient
    public String getAdditionalImagesText() {
        List<String> images = additionalImages == null ? java.util.Collections.emptyList() : additionalImages;
        return String.join(System.lineSeparator(), images);
    }

    @Transient
    public String getDisplayPrimaryImageUrl() {
        if (primaryImageUrl == null) {
            return null;
        }
        String normalized = primaryImageUrl.trim();
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
