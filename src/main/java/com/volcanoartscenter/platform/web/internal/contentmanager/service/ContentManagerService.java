package com.volcanoartscenter.platform.web.internal.contentmanager.service;

import com.volcanoartscenter.platform.shared.model.BlogPost;
import com.volcanoartscenter.platform.shared.model.Experience;
import com.volcanoartscenter.platform.shared.model.Product;
import com.volcanoartscenter.platform.shared.model.ProductCategory;
import com.volcanoartscenter.platform.shared.model.ProductCollection;
import com.volcanoartscenter.platform.shared.model.Review;
import com.volcanoartscenter.platform.shared.model.TalentApplication;
import com.volcanoartscenter.platform.shared.model.TalentProfile;
import com.volcanoartscenter.platform.shared.model.Testimonial;
import com.volcanoartscenter.platform.shared.model.MediaAsset;
import com.volcanoartscenter.platform.shared.repository.ExperienceRepository;
import com.volcanoartscenter.platform.shared.repository.MediaAssetRepository;
import com.volcanoartscenter.platform.shared.repository.BlogPostRepository;
import com.volcanoartscenter.platform.shared.repository.ProductCategoryRepository;
import com.volcanoartscenter.platform.shared.repository.ProductCollectionRepository;
import com.volcanoartscenter.platform.shared.repository.ProductRepository;
import com.volcanoartscenter.platform.shared.repository.ReviewRepository;
import com.volcanoartscenter.platform.shared.repository.TalentProfileRepository;
import com.volcanoartscenter.platform.shared.repository.TestimonialRepository;
import com.volcanoartscenter.platform.web.internal.superadmin.service.SuperAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ContentManagerService {

    private final SuperAdminService superAdminService;
    private final ExperienceRepository experienceRepository;
    private final TestimonialRepository testimonialRepository;
    private final TalentProfileRepository talentProfileRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductCollectionRepository productCollectionRepository;
    private final ReviewRepository reviewRepository;
    private final BlogPostRepository blogPostRepository;

    public long totalProducts() { return productRepository.count(); }
    public long totalCategories() { return productCategoryRepository.count(); }
    public long totalCollections() { return productCollectionRepository.count(); }
    public long totalBlogPosts() { return blogPostRepository.count(); }
    public long totalExperiences() { return experienceRepository.count(); }
    public long totalMediaAssets() { return mediaAssetRepository.count(); }
    public long pendingReviews() {
        return reviewRepository.findTop200ByOrderByCreatedAtDesc().stream().filter(r -> !Boolean.TRUE.equals(r.getApproved())).count();
    }

    public java.util.List<Product> listProducts(Boolean available, Boolean featured, Long categoryId, Long collectionId, String q) {
        return productRepository.searchForCms(available, featured, categoryId, collectionId, q);
    }
    public Product createProduct(String name, String slug, BigDecimal price, Long categoryId, Long collectionId,
                                 String primaryImageUrl, List<String> additionalImages, Integer stockQuantity,
                                 Product.InventoryType inventoryType, String shortDescription, String description,
                                 BigDecimal compareAtPrice, Product.ArtworkStatus artworkStatus, String artistName,
                                 String artistStory, String dimensions, String medium, BigDecimal weightKg,
                                 Boolean shippable, String shippingNote, Boolean available, Boolean featured) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Product slug is required");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Price must be zero or positive");
        }
        String normalizedSlug = slug.trim().toLowerCase(Locale.ROOT);
        if (productRepository.existsBySlug(normalizedSlug)) {
            throw new IllegalArgumentException("Product slug already exists");
        }
        validateProductNumbers(price, compareAtPrice, weightKg);
        ProductCategory category = categoryId == null ? null : productCategoryRepository.findById(categoryId).orElse(null);
        ProductCollection collection = collectionId == null ? null : productCollectionRepository.findById(collectionId).orElse(null);
        Product product = Product.builder()
                .name(name.trim())
                .slug(normalizedSlug)
                .price(price)
                .category(category)
                .collection(collection)
                .build();
        applyProductContent(product, primaryImageUrl, additionalImages, stockQuantity, inventoryType, shortDescription,
                description, compareAtPrice, artworkStatus, artistName, artistStory, dimensions, medium, weightKg,
                shippable, shippingNote, available, featured, category, collection);
        return productRepository.save(product);
    }

    public Product updateProduct(Long id, String name, BigDecimal price, Long categoryId, Long collectionId,
                                 String primaryImageUrl, List<String> additionalImages, Integer stockQuantity,
                                 Product.InventoryType inventoryType, String shortDescription, String description,
                                 BigDecimal compareAtPrice, Product.ArtworkStatus artworkStatus, String artistName,
                                 String artistStory, String dimensions, String medium, BigDecimal weightKg,
                                 Boolean shippable, String shippingNote, Boolean available, Boolean featured) {
        Product product = productRepository.findById(id).orElseThrow();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Price must be zero or positive");
        }
        validateProductNumbers(price, compareAtPrice, weightKg);
        ProductCategory category = categoryId == null ? null : productCategoryRepository.findById(categoryId).orElse(null);
        ProductCollection collection = collectionId == null ? null : productCollectionRepository.findById(collectionId).orElse(null);
        product.setName(name.trim());
        product.setPrice(price);
        applyProductContent(product, primaryImageUrl, additionalImages, stockQuantity, inventoryType, shortDescription,
                description, compareAtPrice, artworkStatus, artistName, artistStory, dimensions, medium, weightKg,
                shippable, shippingNote, available, featured, category, collection);
        return productRepository.save(product);
    }

    public int bulkUpdateProducts(java.util.List<Long> productIds, Boolean available, Boolean featured, Long categoryId, Long collectionId) {
        if (productIds == null || productIds.isEmpty()) {
            return 0;
        }
        ProductCategory category = categoryId == null ? null : productCategoryRepository.findById(categoryId).orElse(null);
        ProductCollection collection = collectionId == null ? null : productCollectionRepository.findById(collectionId).orElse(null);
        int updated = 0;
        for (Long productId : productIds) {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) {
                continue;
            }
            if (available != null) {
                product.setAvailable(available);
            }
            if (featured != null) {
                product.setFeatured(featured);
            }
            if (categoryId != null) {
                product.setCategory(category);
            }
            if (collectionId != null) {
                product.setCollection(collection);
            }
            productRepository.save(product);
            updated++;
        }
        return updated;
    }
    public void deleteProduct(Long id) { superAdminService.deleteProduct(id); }
    public java.util.List<ProductCategory> listCategories() {
        return productCategoryRepository.findAll().stream()
                .sorted(java.util.Comparator
                        .comparing(ProductCategory::getDisplayOrder, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(ProductCategory::getName, java.util.Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }
    public ProductCategory createCategory(String name, String slug, String description, Integer displayOrder, Boolean active) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Category slug is required");
        }
        String normalizedSlug = slug.trim().toLowerCase(Locale.ROOT);
        if (productCategoryRepository.existsBySlug(normalizedSlug)) {
            throw new IllegalArgumentException("Category slug already exists");
        }
        return productCategoryRepository.save(ProductCategory.builder()
                .name(name)
                .slug(normalizedSlug)
                .description(description)
                .displayOrder(displayOrder == null ? 0 : displayOrder)
                .active(Boolean.TRUE.equals(active))
                .build());
    }
    public ProductCategory updateCategory(Long id, String name, String description, Integer displayOrder, Boolean active) {
        ProductCategory category = productCategoryRepository.findById(id).orElseThrow();
        category.setName(name);
        category.setDescription(description);
        category.setDisplayOrder(displayOrder == null ? 0 : displayOrder);
        category.setActive(Boolean.TRUE.equals(active));
        return productCategoryRepository.save(category);
    }
    public void deleteCategory(Long id) { productCategoryRepository.deleteById(id); }
    public java.util.List<ProductCollection> listCollections() {
        return productCollectionRepository.findAll().stream()
                .sorted(java.util.Comparator
                        .comparing(ProductCollection::getDisplayOrder, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(ProductCollection::getName, java.util.Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }
    public ProductCollection createCollection(String name, String slug, String description, Integer displayOrder, Boolean active) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Collection name is required");
        }
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Collection slug is required");
        }
        String normalizedSlug = slug.trim().toLowerCase(Locale.ROOT);
        if (productCollectionRepository.existsBySlug(normalizedSlug)) {
            throw new IllegalArgumentException("Collection slug already exists");
        }
        return productCollectionRepository.save(ProductCollection.builder()
                .name(name)
                .slug(normalizedSlug)
                .description(description)
                .displayOrder(displayOrder == null ? 0 : displayOrder)
                .active(Boolean.TRUE.equals(active))
                .build());
    }
    public ProductCollection updateCollection(Long id, String name, String description, Integer displayOrder, Boolean active) {
        ProductCollection collection = productCollectionRepository.findById(id).orElseThrow();
        collection.setName(name);
        collection.setDescription(description);
        collection.setDisplayOrder(displayOrder == null ? 0 : displayOrder);
        collection.setActive(Boolean.TRUE.equals(active));
        return productCollectionRepository.save(collection);
    }
    public void deleteCollection(Long id) { productCollectionRepository.deleteById(id); }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<BlogPost> listBlogPosts(String q, BlogPost.BlogCategory category) {
        return blogPostRepository.searchForCms(q, category);
    }
    public BlogPost createBlogPost(String title, String slug, String excerpt, String content,
                                   BlogPost.BlogCategory category, Boolean published, String featuredImageUrl,
                                   Long coverMediaId, Boolean highlighted, String metaTitle, String metaDescription,
                                   List<String> additionalImageUrls) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Blog title is required");
        }
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Blog slug is required");
        }
        String normalizedSlug = slug.trim().toLowerCase(Locale.ROOT);
        if (blogPostRepository.findBySlug(normalizedSlug).isPresent()) {
            throw new IllegalArgumentException("Blog post slug already exists");
        }
        boolean publish = Boolean.TRUE.equals(published);
        BlogPost post = BlogPost.builder()
                .title(title)
                .slug(normalizedSlug)
                .excerpt(excerpt)
                .content(content == null ? "" : content)
                .category(category == null ? BlogPost.BlogCategory.UPDATE : category)
                .featuredImageUrl(featuredImageUrl)
                .coverMediaId(coverMediaId)
                .highlighted(Boolean.TRUE.equals(highlighted))
                .metaTitle(metaTitle)
                .metaDescription(metaDescription)
                .published(publish)
                .publishedAt(publish ? java.time.LocalDateTime.now() : null)
                .build();
        if (additionalImageUrls != null && !additionalImageUrls.isEmpty()) {
            post.setAdditionalImages(new java.util.LinkedHashSet<>(additionalImageUrls));
        }
        return blogPostRepository.save(post);
    }
    public BlogPost updateBlogPost(Long id, String title, String excerpt, String content, BlogPost.BlogCategory category, Boolean published,
                                   Long coverMediaId, Boolean highlighted, String featuredImageUrl,
                                   String metaTitle, String metaDescription, List<String> additionalImageUrls) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Blog title is required");
        }
        BlogPost post = blogPostRepository.findById(id).orElseThrow();
        post.setTitle(title);
        post.setExcerpt(excerpt);
        post.setContent(content == null ? "" : content);
        post.setCategory(category == null ? BlogPost.BlogCategory.UPDATE : category);
        boolean publish = Boolean.TRUE.equals(published);
        post.setPublished(publish);
        post.setPublishedAt(publish ? (post.getPublishedAt() == null ? java.time.LocalDateTime.now() : post.getPublishedAt()) : null);
        post.setFeaturedImageUrl(featuredImageUrl);
        post.setCoverMediaId(coverMediaId);
        post.setHighlighted(Boolean.TRUE.equals(highlighted));
        post.setMetaTitle(metaTitle);
        post.setMetaDescription(metaDescription);
        if (additionalImageUrls != null) {
            post.setAdditionalImages(new java.util.LinkedHashSet<>(additionalImageUrls));
        }
        return blogPostRepository.save(post);
    }
    public void deleteBlogPost(Long id) { superAdminService.deleteBlogPost(id); }

    public List<Review> listReviews() { return reviewRepository.findTop200ByOrderByCreatedAtDesc(); }
    public Review approveReview(Long id, Boolean approved, Boolean featured) {
        Review review = reviewRepository.findById(id).orElseThrow();
        review.setApproved(Boolean.TRUE.equals(approved));
        review.setFeatured(Boolean.TRUE.equals(featured));
        if (Boolean.TRUE.equals(approved) && review.getApprovedAt() == null) {
            review.setApprovedAt(java.time.LocalDateTime.now());
        }
        return reviewRepository.save(review);
    }
    public void deleteReview(Long id) { reviewRepository.deleteById(id); }

    public Object listExperiences(Boolean active, String q) {
        return experienceRepository.searchForCms(active, q);
    }

    public Experience createExperience(String title, String slug, String shortDescription, String description,
                                       String location, java.math.BigDecimal pricePerPerson, java.math.BigDecimal groupPrice,
                                       String priceNote, Experience.ExperienceType experienceType,
                                       Experience.BookingType bookingType, Integer minGroupSize, Integer maxGroupSize,
                                       java.math.BigDecimal durationHours, String meetingPoint, String languagesOffered,
                                       String whatsIncluded, String whatToBring, String primaryImageUrl,
                                       List<String> additionalImages, Boolean availableDaily, String availableDays,
                                       Boolean active, Boolean featured, Long primaryMediaId) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Experience title is required");
        }
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Experience slug is required");
        }
        String normalizedSlug = slug.trim().toLowerCase(Locale.ROOT);
        if (experienceRepository.existsBySlug(normalizedSlug)) {
            throw new IllegalArgumentException("Experience slug already exists");
        }
        validateExperienceNumbers(pricePerPerson, groupPrice, durationHours);
        Experience entity = Experience.builder()
                .title(title.trim())
                .slug(normalizedSlug)
                .build();
        applyExperienceContent(entity, shortDescription, description, location, pricePerPerson, groupPrice, priceNote,
                experienceType, bookingType, minGroupSize, maxGroupSize, durationHours, meetingPoint,
                languagesOffered, whatsIncluded, whatToBring, primaryImageUrl, additionalImages, availableDaily,
                availableDays, active, featured, primaryMediaId);
        return experienceRepository.save(entity);
    }

    public Experience updateExperience(Long id, String title, String shortDescription, String description,
                                       String location, java.math.BigDecimal pricePerPerson, java.math.BigDecimal groupPrice,
                                       String priceNote, Experience.ExperienceType experienceType,
                                       Experience.BookingType bookingType, Integer minGroupSize, Integer maxGroupSize,
                                       java.math.BigDecimal durationHours, String meetingPoint, String languagesOffered,
                                       String whatsIncluded, String whatToBring, String primaryImageUrl,
                                       List<String> additionalImages, Boolean availableDaily, String availableDays,
                                       Boolean active, Boolean featured, Long primaryMediaId) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Experience title is required");
        }
        validateExperienceNumbers(pricePerPerson, groupPrice, durationHours);
        Experience entity = experienceRepository.findById(id).orElseThrow();
        entity.setTitle(title.trim());
        applyExperienceContent(entity, shortDescription, description, location, pricePerPerson, groupPrice, priceNote,
                experienceType, bookingType, minGroupSize, maxGroupSize, durationHours, meetingPoint,
                languagesOffered, whatsIncluded, whatToBring, primaryImageUrl, additionalImages, availableDaily,
                availableDays, active, featured, primaryMediaId);
        return experienceRepository.save(entity);
    }

    public void deleteExperience(Long id) {
        experienceRepository.deleteById(id);
    }

    public Object listTestimonials() {
        return testimonialRepository.findAll();
    }

    public Testimonial createTestimonial(String authorName, String authorCountry, String message, Integer rating, Boolean published, Boolean featured,
                                          String authorTitle) {
        if (authorName == null || authorName.isBlank()) {
            throw new IllegalArgumentException("Author name is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message is required");
        }
        return testimonialRepository.save(Testimonial.builder()
                .authorName(authorName)
                .authorCountry(authorCountry)
                .message(message)
                .authorTitle(authorTitle)
                .rating(rating == null ? 5 : Math.max(1, Math.min(5, rating)))
                .published(Boolean.TRUE.equals(published))
                .featured(Boolean.TRUE.equals(featured))
                .build());
    }

    public Testimonial updateTestimonial(Long id, String authorName, String authorCountry, String message, Integer rating, Boolean published, Boolean featured,
                                          String authorTitle) {
        if (authorName == null || authorName.isBlank()) {
            throw new IllegalArgumentException("Author name is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message is required");
        }
        Testimonial testimonial = testimonialRepository.findById(id).orElseThrow();
        testimonial.setAuthorName(authorName);
        testimonial.setAuthorCountry(authorCountry);
        testimonial.setMessage(message);
        testimonial.setAuthorTitle(authorTitle);
        testimonial.setRating(rating == null ? 5 : Math.max(1, Math.min(5, rating)));
        testimonial.setPublished(Boolean.TRUE.equals(published));
        testimonial.setFeatured(Boolean.TRUE.equals(featured));
        return testimonialRepository.save(testimonial);
    }

    public void deleteTestimonial(Long id) {
        testimonialRepository.deleteById(id);
    }

    public Object listTalentProfiles() {
        return talentProfileRepository.findAll();
    }

    public TalentProfile createTalentProfile(String displayName, TalentApplication.ApplicantCategory category, String headline,
                                              String story, String primaryImageUrl, Boolean published, Long primaryMediaId) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }
        if (category == null) {
            throw new IllegalArgumentException("Category is required");
        }
        return talentProfileRepository.save(TalentProfile.builder()
                .displayName(displayName)
                .category(category)
                .headline(headline)
                .story(story)
                .primaryImageUrl(primaryImageUrl)
                .primaryMediaId(primaryMediaId)
                .published(Boolean.TRUE.equals(published))
                .build());
    }

    public TalentProfile updateTalentProfile(Long id, String displayName, TalentApplication.ApplicantCategory category, String headline,
                                              String story, String primaryImageUrl, Boolean published, Long primaryMediaId) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }
        if (category == null) {
            throw new IllegalArgumentException("Category is required");
        }
        TalentProfile profile = talentProfileRepository.findById(id).orElseThrow();
        profile.setDisplayName(displayName);
        profile.setCategory(category);
        profile.setHeadline(headline);
        profile.setStory(story);
        profile.setPrimaryImageUrl(primaryImageUrl);
        profile.setPrimaryMediaId(primaryMediaId);
        profile.setPublished(Boolean.TRUE.equals(published));
        return talentProfileRepository.save(profile);
    }

    public void deleteTalentProfile(Long id) {
        talentProfileRepository.deleteById(id);
    }

    public java.util.List<MediaAsset> listMediaAssets(String q, String contentType) {
        return mediaAssetRepository.searchForCms(q, contentType);
    }

    public MediaAsset createMediaAsset(String storageKey, String publicUrl, String contentType, String title, String altText, Long fileSizeBytes) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key is required");
        }
        if (publicUrl == null || publicUrl.isBlank()) {
            throw new IllegalArgumentException("Public URL is required");
        }
        if (mediaAssetRepository.existsByStorageKey(storageKey)) {
            throw new IllegalArgumentException("Storage key already exists");
        }
        return mediaAssetRepository.save(MediaAsset.builder()
                .storageKey(storageKey)
                .publicUrl(publicUrl)
                .contentType(contentType)
                .title(title)
                .altText(altText)
                .fileSizeBytes(fileSizeBytes)
                .build());
    }

    public void deleteMediaAsset(Long id) {
        mediaAssetRepository.deleteById(id);
    }

    public Optional<MediaAsset> findMediaAsset(Long id) {
        return mediaAssetRepository.findById(id);
    }

    private void applyProductContent(Product product, String primaryImageUrl, List<String> additionalImages,
                                     Integer stockQuantity, Product.InventoryType inventoryType, String shortDescription,
                                     String description, BigDecimal compareAtPrice, Product.ArtworkStatus artworkStatus,
                                     String artistName, String artistStory, String dimensions, String medium,
                                     BigDecimal weightKg, Boolean shippable, String shippingNote, Boolean available,
                                     Boolean featured, ProductCategory category, ProductCollection collection) {
        String normalizedShortDescription = normalizeText(shortDescription);
        String normalizedDescription = normalizeText(description);
        if (normalizedDescription == null) {
            normalizedDescription = normalizedShortDescription;
        }

        product.setShortDescription(normalizedShortDescription);
        product.setDescription(normalizedDescription);
        product.setCompareAtPrice(compareAtPrice);
        product.setCategory(category);
        product.setCollection(collection);
        product.setPrimaryImageUrl(normalizeText(primaryImageUrl));
        product.setAdditionalImages(normalizeAdditionalImages(additionalImages));
        product.setStockQuantity(stockQuantity == null ? 1 : Math.max(0, stockQuantity));
        product.setInventoryType(inventoryType == null ? Product.InventoryType.BATCH : inventoryType);
        product.setAvailable(Boolean.TRUE.equals(available));
        product.setArtworkStatus(artworkStatus == null ? Product.ArtworkStatus.PUBLISHED : artworkStatus);
        product.setFeatured(Boolean.TRUE.equals(featured));
        product.setArtistName(normalizeText(artistName));
        product.setArtistStory(normalizeText(artistStory));
        product.setDimensions(normalizeText(dimensions));
        product.setMedium(normalizeText(medium));
        product.setWeightKg(weightKg);
        product.setShippable(Boolean.TRUE.equals(shippable));
        product.setShippingNote(normalizeText(shippingNote));
    }

    private void validateProductNumbers(BigDecimal price, BigDecimal compareAtPrice, BigDecimal weightKg) {
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Price must be zero or positive");
        }
        if (compareAtPrice != null && compareAtPrice.signum() < 0) {
            throw new IllegalArgumentException("Compare-at price must be zero or positive");
        }
        if (weightKg != null && weightKg.signum() < 0) {
            throw new IllegalArgumentException("Weight must be zero or positive");
        }
    }

    private void applyExperienceContent(Experience experience, String shortDescription, String description,
                                        String location, BigDecimal pricePerPerson, BigDecimal groupPrice,
                                        String priceNote, Experience.ExperienceType experienceType,
                                        Experience.BookingType bookingType, Integer minGroupSize,
                                        Integer maxGroupSize, BigDecimal durationHours, String meetingPoint,
                                        String languagesOffered, String whatsIncluded, String whatToBring,
                                        String primaryImageUrl, List<String> additionalImages, Boolean availableDaily,
                                        String availableDays, Boolean active, Boolean featured, Long primaryMediaId) {
        String normalizedShortDescription = normalizeText(shortDescription);
        String normalizedDescription = normalizeText(description);
        if (normalizedDescription == null) {
            normalizedDescription = normalizedShortDescription == null ? "" : normalizedShortDescription;
        }

        experience.setDescription(normalizedDescription);
        experience.setShortDescription(normalizedShortDescription);
        experience.setLocation(normalizeText(location));
        experience.setPricePerPerson(pricePerPerson);
        experience.setGroupPrice(groupPrice);
        experience.setPriceNote(normalizeText(priceNote));
        experience.setExperienceType(experienceType == null ? Experience.ExperienceType.CULTURAL : experienceType);
        experience.setBookingType(bookingType == null ? Experience.BookingType.INQUIRY : bookingType);
        experience.setMinGroupSize(minGroupSize == null ? 1 : Math.max(1, minGroupSize));
        experience.setMaxGroupSize(maxGroupSize == null ? 15 : Math.max(1, maxGroupSize));
        experience.setDurationHours(durationHours);
        experience.setMeetingPoint(normalizeText(meetingPoint));
        experience.setLanguagesOffered(normalizeText(languagesOffered) == null ? "English, French" : normalizeText(languagesOffered));
        experience.setWhatsIncluded(normalizeText(whatsIncluded));
        experience.setWhatToBring(normalizeText(whatToBring));
        experience.setPrimaryImageUrl(normalizeText(primaryImageUrl));
        experience.setAdditionalImages(normalizeAdditionalImages(additionalImages));
        experience.setAvailableDaily(availableDaily == null || Boolean.TRUE.equals(availableDaily));
        experience.setAvailableDays(normalizeText(availableDays));
        experience.setActive(Boolean.TRUE.equals(active));
        experience.setFeatured(Boolean.TRUE.equals(featured));
        experience.setPrimaryMediaId(primaryMediaId);
    }

    private void validateExperienceNumbers(BigDecimal pricePerPerson, BigDecimal groupPrice, BigDecimal durationHours) {
        if (pricePerPerson != null && pricePerPerson.signum() < 0) {
            throw new IllegalArgumentException("Price per person must be zero or positive");
        }
        if (groupPrice != null && groupPrice.signum() < 0) {
            throw new IllegalArgumentException("Group price must be zero or positive");
        }
        if (durationHours != null && durationHours.signum() < 0) {
            throw new IllegalArgumentException("Duration must be zero or positive");
        }
    }

    private List<String> normalizeAdditionalImages(List<String> additionalImages) {
        if (additionalImages == null || additionalImages.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String imageUrl : additionalImages) {
            String value = normalizeText(imageUrl);
            if (value != null) {
                normalized.add(value);
            }
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
