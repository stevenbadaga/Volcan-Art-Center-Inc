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
@Table(name = "products")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "compare_at_price", precision = 12, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(nullable = false, unique = true, length = 250)
    private String slug;

    // Inventory type: UNIQUE (one-of-a-kind art), BATCH (handcrafts with stock)
    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_type", nullable = false, length = 20)
    @Builder.Default
    private InventoryType inventoryType = InventoryType.UNIQUE;

    @Column(name = "stock_quantity")
    @Builder.Default
    private Integer stockQuantity = 1;

    @Column(name = "reserved_quantity")
    @Builder.Default
    private Integer reservedQuantity = 0;

    @Column(name = "reserved_until")
    private LocalDateTime reservedUntil;

    @Column(nullable = false)
    @Builder.Default
    private Boolean available = true;

    // Artwork lifecycle status
    @Enumerated(EnumType.STRING)
    @Column(name = "artwork_status", nullable = false, length = 20)
    @Builder.Default
    private ArtworkStatus artworkStatus = ArtworkStatus.PUBLISHED;

    @Column(name = "featured")
    @Builder.Default
    private Boolean featured = false;

    // Artist / creator info
    @Column(name = "artist_name", length = 150)
    private String artistName;

    @Column(name = "artist_story", columnDefinition = "TEXT")
    private String artistStory;

    // Dimensions & physical details
    @Column(length = 100)
    private String dimensions;

    @Column(length = 100)
    private String medium;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    // Images
    @Column(name = "primary_image_url")
    private String primaryImageUrl;

    @ElementCollection
    @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url")
    @Builder.Default
    private List<String> additionalImages = new ArrayList<>();

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id")
    private ProductCollection collection;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();

    // Shipping
    @Column(name = "shippable")
    @Builder.Default
    private Boolean shippable = true;

    @Column(name = "shipping_note", length = 500)
    private String shippingNote;

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

    public boolean isInStock() {
        if (artworkStatus != ArtworkStatus.PUBLISHED) {
            return false;
        }
        if (inventoryType == InventoryType.UNIQUE) {
            return available;
        }
        return stockQuantity != null && stockQuantity > 0;
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
        if (lower.contains("logo") || lower.contains("placeholder") || lower.contains("default")
                || lower.contains("whatsapp") || lower.contains("sabbath")) {
            return null;
        }
        return normalized;
    }

    public enum InventoryType {
        UNIQUE,   // One-of-a-kind artwork
        BATCH     // Handcrafts with multiple units
    }

    public enum ArtworkStatus {
        DRAFT,       // Not yet visible on public catalog
        PUBLISHED,   // Live and available for purchase
        SOLD,        // Sold out (unique pieces)
        ARCHIVED     // Removed from catalog but preserved for order history
    }
}
