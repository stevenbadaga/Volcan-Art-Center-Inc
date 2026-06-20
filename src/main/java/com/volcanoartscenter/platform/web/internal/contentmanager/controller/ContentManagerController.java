package com.volcanoartscenter.platform.web.internal.contentmanager.controller;

import com.volcanoartscenter.platform.shared.model.BlogPost;
import com.volcanoartscenter.platform.shared.model.Experience;
import com.volcanoartscenter.platform.shared.model.Product;
import com.volcanoartscenter.platform.shared.model.Review;
import com.volcanoartscenter.platform.shared.model.TalentApplication;
import com.volcanoartscenter.platform.web.internal.contentmanager.service.ContentManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ContentManagerController {

    private final ContentManagerService contentManagerService;
    @Value("${platform.storage.local-upload-dir:${user.home}/.volcano-platform/uploads}")
    private String localUploadDir;

    @GetMapping("/admin/content/dashboard")
    public String contentDashboard(Model model) {
        var products = contentManagerService.listProducts(null, null, null, null, null);
        var recentMedia = contentManagerService.listMediaAssets(null, null).stream().limit(6).toList();
        model.addAttribute("adminPage", "content-dashboard");
        model.addAttribute("pageTitle", "Content Dashboard");
        model.addAttribute("totalProducts", contentManagerService.totalProducts());
        model.addAttribute("totalCategories", contentManagerService.totalCategories());
        model.addAttribute("totalCollections", contentManagerService.totalCollections());
        model.addAttribute("totalBlogPosts", contentManagerService.totalBlogPosts());
        model.addAttribute("totalExperiences", contentManagerService.totalExperiences());
        model.addAttribute("totalMediaAssets", contentManagerService.totalMediaAssets());
        model.addAttribute("pendingReviews", contentManagerService.pendingReviews());
        model.addAttribute("availableProducts", products.stream().filter(p -> Boolean.TRUE.equals(p.getAvailable())).count());
        model.addAttribute("featuredProducts", products.stream().filter(p -> Boolean.TRUE.equals(p.getFeatured())).count());
        model.addAttribute("lowStockProducts", products.stream()
                .filter(p -> p.getStockQuantity() != null && p.getStockQuantity() <= 3)
                .count());
        model.addAttribute("recentProducts", products.stream().limit(6).toList());
        model.addAttribute("recentMedia", recentMedia);
        return "internal/content-manager/dashboard";
    }

    @GetMapping("/admin/content/products")
    public String contentProducts(@RequestParam(required = false) Boolean available,
                                  @RequestParam(required = false) Boolean featured,
                                  @RequestParam(required = false) Long categoryId,
                                  @RequestParam(required = false) Long collectionId,
                                  @RequestParam(required = false) String q,
                                  Model model) {
        model.addAttribute("adminPage", "products");
        model.addAttribute("pageTitle", "Art Catalog");
        model.addAttribute("items", contentManagerService.listProducts(available, featured, categoryId, collectionId, q));
        model.addAttribute("categories", contentManagerService.listCategories());
        model.addAttribute("collections", contentManagerService.listCollections());
        model.addAttribute("inventoryTypes", Product.InventoryType.values());
        model.addAttribute("artworkStatuses", Product.ArtworkStatus.values());
        model.addAttribute("available", available);
        model.addAttribute("featured", featured);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("collectionId", collectionId);
        model.addAttribute("q", q);
        model.addAttribute("totalProducts", contentManagerService.totalProducts());
        model.addAttribute("totalCategories", contentManagerService.totalCategories());
        model.addAttribute("totalCollections", contentManagerService.totalCollections());
        model.addAttribute("totalBlogPosts", contentManagerService.totalBlogPosts());
        model.addAttribute("totalExperiences", contentManagerService.totalExperiences());
        model.addAttribute("totalMediaAssets", contentManagerService.totalMediaAssets());
        model.addAttribute("pendingReviews", contentManagerService.pendingReviews());
        return "internal/content-manager/products";
    }

    @PostMapping("/admin/content/products")
    public String createContentProduct(@RequestParam String name,
                                       @RequestParam String slug,
                                       @RequestParam BigDecimal price,
                                       @RequestParam(required = false) String shortDescription,
                                       @RequestParam(required = false) String description,
                                       @RequestParam(required = false) BigDecimal compareAtPrice,
                                       @RequestParam(required = false) Long categoryId,
                                       @RequestParam(required = false) Long collectionId,
                                       @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                       @RequestParam(required = false) String primaryImageUrl,
                                       @RequestParam(value = "additionalImageFiles", required = false) List<MultipartFile> additionalImageFiles,
                                       @RequestParam(required = false) String additionalImageUrls,
                                       @RequestParam(required = false) String artistName,
                                       @RequestParam(required = false) String artistStory,
                                       @RequestParam(required = false) String dimensions,
                                       @RequestParam(required = false) String medium,
                                       @RequestParam(required = false) BigDecimal weightKg,
                                       @RequestParam(defaultValue = "1") Integer stockQuantity,
                                       @RequestParam(defaultValue = "BATCH") Product.InventoryType inventoryType,
                                       @RequestParam(defaultValue = "PUBLISHED") Product.ArtworkStatus artworkStatus,
                                       @RequestParam(defaultValue = "false") Boolean available,
                                       @RequestParam(defaultValue = "false") Boolean featured,
                                       @RequestParam(defaultValue = "false") Boolean shippable,
                                       @RequestParam(required = false) String shippingNote,
                                       RedirectAttributes redirectAttributes) {
        try {
            String finalImageUrl = normalizeOptionalImageUrl(primaryImageUrl);
            if (imageFile != null && !imageFile.isEmpty()) {
                finalImageUrl = handleFileUpload(imageFile, "Product Image");
            }
            List<String> additionalImages = collectAdditionalImageUrls(additionalImageUrls, additionalImageFiles, finalImageUrl, "Product Image");
            if (finalImageUrl == null && !additionalImages.isEmpty()) {
                finalImageUrl = additionalImages.remove(0);
            }
            contentManagerService.createProduct(name, slug, price, categoryId, collectionId, finalImageUrl, additionalImages,
                    stockQuantity, inventoryType, shortDescription, description, compareAtPrice, artworkStatus,
                    artistName, artistStory, dimensions, medium, weightKg, shippable, shippingNote, available, featured);
            redirectAttributes.addFlashAttribute("successMessage", "Product created.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/content/products";
    }

    @PostMapping("/admin/content/products/{id}/update")
    public String updateContentProduct(@PathVariable Long id,
                                       @RequestParam String name,
                                       @RequestParam BigDecimal price,
                                       @RequestParam(required = false) String shortDescription,
                                       @RequestParam(required = false) String description,
                                       @RequestParam(required = false) BigDecimal compareAtPrice,
                                       @RequestParam(required = false) Long categoryId,
                                       @RequestParam(required = false) Long collectionId,
                                       @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                       @RequestParam(required = false) String primaryImageUrl,
                                       @RequestParam(value = "additionalImageFiles", required = false) List<MultipartFile> additionalImageFiles,
                                       @RequestParam(required = false) String additionalImageUrls,
                                       @RequestParam(required = false) String artistName,
                                       @RequestParam(required = false) String artistStory,
                                       @RequestParam(required = false) String dimensions,
                                       @RequestParam(required = false) String medium,
                                       @RequestParam(required = false) BigDecimal weightKg,
                                       @RequestParam(required = false) Integer stockQuantity,
                                       @RequestParam(defaultValue = "BATCH") Product.InventoryType inventoryType,
                                       @RequestParam(defaultValue = "PUBLISHED") Product.ArtworkStatus artworkStatus,
                                       @RequestParam(defaultValue = "false") Boolean available,
                                       @RequestParam(defaultValue = "false") Boolean featured,
                                       @RequestParam(defaultValue = "false") Boolean shippable,
                                       @RequestParam(required = false) String shippingNote,
                                       RedirectAttributes redirectAttributes) {
        try {
            String finalImageUrl = normalizeOptionalImageUrl(primaryImageUrl);
            if (imageFile != null && !imageFile.isEmpty()) {
                finalImageUrl = handleFileUpload(imageFile, "Product Image");
            }
            List<String> additionalImages = collectAdditionalImageUrls(additionalImageUrls, additionalImageFiles, finalImageUrl, "Product Image");
            if (finalImageUrl == null && !additionalImages.isEmpty()) {
                finalImageUrl = additionalImages.remove(0);
            }
            contentManagerService.updateProduct(id, name, price, categoryId, collectionId, finalImageUrl, additionalImages,
                    stockQuantity, inventoryType, shortDescription, description, compareAtPrice, artworkStatus,
                    artistName, artistStory, dimensions, medium, weightKg, shippable, shippingNote, available, featured);
            redirectAttributes.addFlashAttribute("successMessage", "Product updated.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/content/products";
    }

    @PostMapping("/admin/content/products/bulk-update")
    public String bulkUpdateProducts(@RequestParam String productIdsCsv,
                                     @RequestParam(required = false) Boolean available,
                                     @RequestParam(required = false) Boolean featured,
                                     @RequestParam(required = false) Long categoryId,
                                     @RequestParam(required = false) Long collectionId,
                                     RedirectAttributes redirectAttributes) {
        java.util.List<Long> productIds;
        try {
            productIds = java.util.Arrays.stream(productIdsCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(Long::valueOf)
                    .toList();
        } catch (NumberFormatException ex) {
            redirectAttributes.addFlashAttribute("successMessage", "Product IDs must be numeric values separated by commas.");
            return "redirect:/admin/content/products";
        }
        if (productIds == null || productIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("successMessage", "Select at least one product.");
            return "redirect:/admin/content/products";
        }
        int updated = contentManagerService.bulkUpdateProducts(productIds, available, featured, categoryId, collectionId);
        redirectAttributes.addFlashAttribute("successMessage", "Bulk update applied to " + updated + " products.");
        return "redirect:/admin/content/products";
    }

    @PostMapping("/admin/content/products/{id}/delete")
    public String deleteContentProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        contentManagerService.deleteProduct(id);
        redirectAttributes.addFlashAttribute("successMessage", "Product deleted.");
        return "redirect:/admin/content/products";
    }

    @PostMapping("/admin/content/categories")
    public String createCategory(@RequestParam String name,
                                 @RequestParam String slug,
                                 @RequestParam(required = false) String description,
                                 @RequestParam(defaultValue = "0") Integer displayOrder,
                                 @RequestParam(defaultValue = "true") Boolean active,
                                 RedirectAttributes redirectAttributes) {
        try {
            contentManagerService.createCategory(name, slug, description, displayOrder, active);
            redirectAttributes.addFlashAttribute("successMessage", "Category created.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/content/products";
    }

    @PostMapping("/admin/content/categories/{id}/update")
    public String updateCategory(@PathVariable Long id,
                                 @RequestParam String name,
                                 @RequestParam(required = false) String description,
                                 @RequestParam(defaultValue = "0") Integer displayOrder,
                                 @RequestParam(defaultValue = "false") Boolean active,
                                 RedirectAttributes redirectAttributes) {
        try {
            contentManagerService.updateCategory(id, name, description, displayOrder, active);
            redirectAttributes.addFlashAttribute("successMessage", "Category updated.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/content/products";
    }

    @PostMapping("/admin/content/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        contentManagerService.deleteCategory(id);
        redirectAttributes.addFlashAttribute("successMessage", "Category deleted.");
        return "redirect:/admin/content/products";
    }

    @PostMapping("/admin/content/collections")
    public String createCollection(@RequestParam String name,
                                   @RequestParam String slug,
                                   @RequestParam(required = false) String description,
                                   @RequestParam(defaultValue = "0") Integer displayOrder,
                                   @RequestParam(defaultValue = "true") Boolean active,
                                   RedirectAttributes redirectAttributes) {
        try {
            contentManagerService.createCollection(name, slug, description, displayOrder, active);
            redirectAttributes.addFlashAttribute("successMessage", "Collection created.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/content/products";
    }

    @PostMapping("/admin/content/collections/{id}/update")
    public String updateCollection(@PathVariable Long id,
                                   @RequestParam String name,
                                   @RequestParam(required = false) String description,
                                   @RequestParam(defaultValue = "0") Integer displayOrder,
                                   @RequestParam(defaultValue = "false") Boolean active,
                                   RedirectAttributes redirectAttributes) {
        try {
            contentManagerService.updateCollection(id, name, description, displayOrder, active);
            redirectAttributes.addFlashAttribute("successMessage", "Collection updated.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/content/products";
    }

    @PostMapping("/admin/content/collections/{id}/delete")
    public String deleteCollection(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        contentManagerService.deleteCollection(id);
        redirectAttributes.addFlashAttribute("successMessage", "Collection deleted.");
        return "redirect:/admin/content/products";
    }

    @GetMapping("/admin/content/blog-posts")
    public String contentBlogPosts(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) BlogPost.BlogCategory category,
                                   Model model) {
        model.addAttribute("adminPage", "blog-posts");
        model.addAttribute("pageTitle", "Blog Posts");
        model.addAttribute("items", contentManagerService.listBlogPosts(q, category));
        model.addAttribute("blogCategories", BlogPost.BlogCategory.values());
        model.addAttribute("q", q);
        model.addAttribute("selectedCategory", category);

        // Safe default blog form values for the creation form
        BlogPost newPost = BlogPost.builder()
                .title("")
                .slug("")
                .excerpt("")
                .content("")
                .featuredImageUrl("")
                .published(false)
                .category(BlogPost.BlogCategory.UPDATE)
                .highlighted(false)
                .tags(java.util.Collections.emptySet())
                .build();
        model.addAttribute("newPost", newPost);

        return "internal/content-manager/blog-posts";
    }

    @PostMapping("/admin/content/blog-posts")
    public String createContentBlogPost(@RequestParam String title,
                                        @RequestParam String slug,
                                        @RequestParam(required = false) String excerpt,
                                        @RequestParam(required = false) String content,
                                        @RequestParam(defaultValue = "UPDATE") BlogPost.BlogCategory category,
                                        @RequestParam(defaultValue = "false") Boolean published,
                                        @RequestParam(required = false) String featuredImageUrl,
                                        @RequestParam(value = "imageFile", required = false) org.springframework.web.multipart.MultipartFile imageFile,
                                        @RequestParam(value = "additionalImageFiles", required = false) List<org.springframework.web.multipart.MultipartFile> additionalImageFiles,
                                        @RequestParam(required = false) String additionalImageUrls,
                                        @RequestParam(required = false) Long coverMediaId,
                                        @RequestParam(defaultValue = "false") Boolean highlighted,
                                        @RequestParam(required = false) String metaTitle,
                                        @RequestParam(required = false) String metaDescription,
                                        RedirectAttributes redirectAttributes) {
        try {
            String finalImageUrl = normalizeOptionalImageUrl(featuredImageUrl);
            if (imageFile != null && !imageFile.isEmpty()) {
                finalImageUrl = handleFileUpload(imageFile, "Blog Featured Image");
            }
            List<String> galleryUrls = collectAdditionalImageUrls(additionalImageUrls, additionalImageFiles, finalImageUrl, "Blog Gallery Image");
            contentManagerService.createBlogPost(title, slug, excerpt, content, category, published, finalImageUrl, coverMediaId, highlighted, metaTitle, metaDescription, galleryUrls);
            redirectAttributes.addFlashAttribute("successMessage", "Blog post created.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/content/blog-posts";
    }

    @PostMapping("/admin/content/blog-posts/{id}/update")
    public String updateContentBlogPost(@PathVariable Long id,
                                        @RequestParam String title,
                                        @RequestParam(required = false) String excerpt,
                                        @RequestParam(required = false) String content,
                                         @RequestParam(defaultValue = "UPDATE") BlogPost.BlogCategory category,
                                         @RequestParam(defaultValue = "false") Boolean published,
                                         @RequestParam(required = false) String featuredImageUrl,
                                         @RequestParam(value = "imageFile", required = false) org.springframework.web.multipart.MultipartFile imageFile,
                                         @RequestParam(value = "additionalImageFiles", required = false) List<org.springframework.web.multipart.MultipartFile> additionalImageFiles,
                                         @RequestParam(required = false) String additionalImageUrls,
                                         @RequestParam(required = false) Long coverMediaId,
                                         @RequestParam(defaultValue = "false") Boolean highlighted,
                                         @RequestParam(required = false) String metaTitle,
                                         @RequestParam(required = false) String metaDescription,
                                         RedirectAttributes redirectAttributes) {
        try {
            String finalImageUrl = normalizeOptionalImageUrl(featuredImageUrl);
            if (imageFile != null && !imageFile.isEmpty()) {
                finalImageUrl = handleFileUpload(imageFile, "Blog Featured Image");
            }
            List<String> galleryUrls = null;
            if ((additionalImageUrls != null && !additionalImageUrls.isBlank())
                    || (additionalImageFiles != null && additionalImageFiles.stream().anyMatch(f -> f != null && !f.isEmpty()))) {
                galleryUrls = collectAdditionalImageUrls(additionalImageUrls, additionalImageFiles, finalImageUrl, "Blog Gallery Image");
            }
            contentManagerService.updateBlogPost(id, title, excerpt, content, category, published, coverMediaId, highlighted, finalImageUrl, metaTitle, metaDescription, galleryUrls);
            redirectAttributes.addFlashAttribute("successMessage", "Blog post updated.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/content/blog-posts";
    }

    @PostMapping("/admin/content/blog-posts/{id}/delete")
    public String deleteContentBlogPost(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        contentManagerService.deleteBlogPost(id);
        redirectAttributes.addFlashAttribute("successMessage", "Blog post deleted.");
        return "redirect:/admin/content/blog-posts";
    }

    @GetMapping("/admin/content/reviews")
    public String contentReviews(Model model) {
        List<Review> items = contentManagerService.listReviews();
        model.addAttribute("adminPage", "reviews");
        model.addAttribute("pageTitle", "Reviews");
        model.addAttribute("items", items);
        model.addAttribute("pendingCount", items.stream().filter(r -> !Boolean.TRUE.equals(r.getApproved())).count());
        model.addAttribute("approvedCount", items.stream().filter(r -> Boolean.TRUE.equals(r.getApproved())).count());
        model.addAttribute("featuredCount", items.stream().filter(r -> Boolean.TRUE.equals(r.getFeatured())).count());
        return "internal/content-manager/reviews";
    }

    @PostMapping("/admin/content/reviews/{id}/moderate")
    public String moderateReview(@PathVariable Long id,
                                 @RequestParam(defaultValue = "false") Boolean approved,
                                 @RequestParam(defaultValue = "false") Boolean featured,
                                 RedirectAttributes redirectAttributes) {
        contentManagerService.approveReview(id, approved, featured);
        redirectAttributes.addFlashAttribute("successMessage", "Review moderation updated.");
        return "redirect:/admin/content/reviews";
    }

    @PostMapping("/admin/content/reviews/{id}/delete")
    public String deleteReview(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        contentManagerService.deleteReview(id);
        redirectAttributes.addFlashAttribute("successMessage", "Review deleted.");
        return "redirect:/admin/content/reviews";
    }

    @GetMapping("/admin/content/experiences")
    public String contentExperiences(@RequestParam(required = false) Boolean active,
                                     @RequestParam(required = false) String q,
                                     Model model) {
        model.addAttribute("adminPage", "experiences");
        model.addAttribute("pageTitle", "Experiences");
        model.addAttribute("items", contentManagerService.listExperiences(active, q));
        model.addAttribute("types", Experience.ExperienceType.values());
        model.addAttribute("bookingTypes", Experience.BookingType.values());
        model.addAttribute("active", active);
        model.addAttribute("q", q);
        model.addAttribute("mediaItems", contentManagerService.listMediaAssets(null, null));
        return "internal/content-manager/experiences";
    }

    @PostMapping("/admin/content/experiences")
    public String createExperience(@RequestParam String title,
                                   @RequestParam String slug,
                                   @RequestParam(required = false) String shortDescription,
                                   @RequestParam(required = false) String description,
                                   @RequestParam(required = false) String location,
                                   @RequestParam(required = false) BigDecimal pricePerPerson,
                                   @RequestParam(required = false) BigDecimal groupPrice,
                                   @RequestParam(required = false) String priceNote,
                                   @RequestParam Experience.ExperienceType experienceType,
                                   @RequestParam Experience.BookingType bookingType,
                                   @RequestParam(defaultValue = "1") Integer minGroupSize,
                                   @RequestParam(defaultValue = "15") Integer maxGroupSize,
                                   @RequestParam(required = false) BigDecimal durationHours,
                                   @RequestParam(required = false) String meetingPoint,
                                   @RequestParam(required = false) String languagesOffered,
                                   @RequestParam(required = false) String whatsIncluded,
                                   @RequestParam(required = false) String whatToBring,
                                   @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                   @RequestParam(required = false) String primaryImageUrl,
                                   @RequestParam(value = "additionalImageFiles", required = false) List<MultipartFile> additionalImageFiles,
                                   @RequestParam(required = false) String additionalImageUrls,
                                   @RequestParam(defaultValue = "true") Boolean availableDaily,
                                   @RequestParam(required = false) String availableDays,
                                   @RequestParam(defaultValue = "true") Boolean active,
                                   @RequestParam(defaultValue = "false") Boolean featured,
                                   @RequestParam(required = false) Long primaryMediaId,
                                    RedirectAttributes redirectAttributes) {
        try {
            String finalImageUrl = normalizeOptionalImageUrl(primaryImageUrl);
            if (imageFile != null && !imageFile.isEmpty()) {
                finalImageUrl = handleFileUpload(imageFile, "Experience Image");
            }
            List<String> additionalImages = collectAdditionalImageUrls(additionalImageUrls, additionalImageFiles, finalImageUrl, "Experience Image");
            if (finalImageUrl == null && !additionalImages.isEmpty()) {
                finalImageUrl = additionalImages.remove(0);
            }
            contentManagerService.createExperience(title, slug, shortDescription, description, location, pricePerPerson, groupPrice,
                    priceNote, experienceType, bookingType, minGroupSize, maxGroupSize, durationHours, meetingPoint,
                    languagesOffered, whatsIncluded, whatToBring, finalImageUrl, additionalImages, availableDaily,
                    availableDays, active, featured, primaryMediaId);
            redirectAttributes.addFlashAttribute("successMessage", "Experience created.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/content/experiences";
    }

    @PostMapping("/admin/content/experiences/{id}/update")
    public String updateExperience(@PathVariable Long id,
                                   @RequestParam String title,
                                   @RequestParam(required = false) String shortDescription,
                                   @RequestParam(required = false) String description,
                                   @RequestParam(required = false) String location,
                                   @RequestParam(required = false) BigDecimal pricePerPerson,
                                   @RequestParam(required = false) BigDecimal groupPrice,
                                   @RequestParam(required = false) String priceNote,
                                   @RequestParam Experience.ExperienceType experienceType,
                                   @RequestParam Experience.BookingType bookingType,
                                   @RequestParam(defaultValue = "1") Integer minGroupSize,
                                   @RequestParam(defaultValue = "15") Integer maxGroupSize,
                                   @RequestParam(required = false) BigDecimal durationHours,
                                   @RequestParam(required = false) String meetingPoint,
                                   @RequestParam(required = false) String languagesOffered,
                                   @RequestParam(required = false) String whatsIncluded,
                                   @RequestParam(required = false) String whatToBring,
                                   @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                   @RequestParam(required = false) String primaryImageUrl,
                                   @RequestParam(value = "additionalImageFiles", required = false) List<MultipartFile> additionalImageFiles,
                                   @RequestParam(required = false) String additionalImageUrls,
                                   @RequestParam(defaultValue = "true") Boolean availableDaily,
                                   @RequestParam(required = false) String availableDays,
                                   @RequestParam(defaultValue = "false") Boolean active,
                                   @RequestParam(defaultValue = "false") Boolean featured,
                                   @RequestParam(required = false) Long primaryMediaId,
                                    RedirectAttributes redirectAttributes) {
        try {
            String finalImageUrl = normalizeOptionalImageUrl(primaryImageUrl);
            if (imageFile != null && !imageFile.isEmpty()) {
                finalImageUrl = handleFileUpload(imageFile, "Experience Image");
            }
            List<String> additionalImages = collectAdditionalImageUrls(additionalImageUrls, additionalImageFiles, finalImageUrl, "Experience Image");
            if (finalImageUrl == null && !additionalImages.isEmpty()) {
                finalImageUrl = additionalImages.remove(0);
            }
            contentManagerService.updateExperience(id, title, shortDescription, description, location, pricePerPerson, groupPrice,
                    priceNote, experienceType, bookingType, minGroupSize, maxGroupSize, durationHours, meetingPoint,
                    languagesOffered, whatsIncluded, whatToBring, finalImageUrl, additionalImages, availableDaily,
                    availableDays, active, featured, primaryMediaId);
            redirectAttributes.addFlashAttribute("successMessage", "Experience updated.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/content/experiences";
    }

    @PostMapping("/admin/content/experiences/{id}/delete")
    public String deleteExperience(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        contentManagerService.deleteExperience(id);
        redirectAttributes.addFlashAttribute("successMessage", "Experience deleted.");
        return "redirect:/admin/content/experiences";
    }

    @GetMapping("/admin/content/testimonials")
    public String contentTestimonials(Model model) {
        model.addAttribute("adminPage", "testimonials");
        model.addAttribute("pageTitle", "Testimonials");
        model.addAttribute("items", contentManagerService.listTestimonials());
        return "internal/content-manager/testimonials";
    }

    @PostMapping("/admin/content/testimonials")
    public String createTestimonial(@RequestParam String authorName,
                                    @RequestParam(required = false) String authorCountry,
                                    @RequestParam String message,
                                    @RequestParam(required = false) String authorTitle,
                                    @RequestParam(defaultValue = "5") Integer rating,
                                    @RequestParam(defaultValue = "false") Boolean published,
                                    @RequestParam(defaultValue = "false") Boolean featured,
                                    RedirectAttributes redirectAttributes) {
        try {
            contentManagerService.createTestimonial(authorName, authorCountry, message, rating, published, featured, authorTitle);
            redirectAttributes.addFlashAttribute("successMessage", "Testimonial created.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/content/testimonials";
    }

    @PostMapping("/admin/content/testimonials/{id}/update")
    public String updateTestimonial(@PathVariable Long id,
                                    @RequestParam String authorName,
                                    @RequestParam(required = false) String authorCountry,
                                    @RequestParam String message,
                                    @RequestParam(required = false) String authorTitle,
                                    @RequestParam(defaultValue = "5") Integer rating,
                                    @RequestParam(defaultValue = "false") Boolean published,
                                    @RequestParam(defaultValue = "false") Boolean featured,
                                    RedirectAttributes redirectAttributes) {
        try {
            contentManagerService.updateTestimonial(id, authorName, authorCountry, message, rating, published, featured, authorTitle);
            redirectAttributes.addFlashAttribute("successMessage", "Testimonial updated.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/content/testimonials";
    }

    @PostMapping("/admin/content/testimonials/{id}/delete")
    public String deleteTestimonial(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        contentManagerService.deleteTestimonial(id);
        redirectAttributes.addFlashAttribute("successMessage", "Testimonial deleted.");
        return "redirect:/admin/content/testimonials";
    }

    @GetMapping("/admin/content/talent-showcase")
    public String contentTalentShowcase(Model model) {
        model.addAttribute("adminPage", "talent-showcase");
        model.addAttribute("pageTitle", "Talent Showcase");
        model.addAttribute("items", contentManagerService.listTalentProfiles());
        model.addAttribute("categories", TalentApplication.ApplicantCategory.values());
        return "internal/content-manager/talent-showcase";
    }

    @PostMapping("/admin/content/talent-showcase")
    public String createTalentShowcase(@RequestParam String displayName,
                                       @RequestParam TalentApplication.ApplicantCategory category,
                                       @RequestParam(required = false) String headline,
                                       @RequestParam(required = false) String story,
                                       @RequestParam(required = false) String primaryImageUrl,
                                       @RequestParam(required = false) Long primaryMediaId,
                                       @RequestParam(defaultValue = "false") Boolean published,
                                       RedirectAttributes redirectAttributes) {
        try {
            contentManagerService.createTalentProfile(displayName, category, headline, story, primaryImageUrl, published, primaryMediaId);
            redirectAttributes.addFlashAttribute("successMessage", "Talent profile created.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/content/talent-showcase";
    }

    @PostMapping("/admin/content/talent-showcase/{id}/update")
    public String updateTalentShowcase(@PathVariable Long id,
                                       @RequestParam String displayName,
                                       @RequestParam TalentApplication.ApplicantCategory category,
                                       @RequestParam(required = false) String headline,
                                       @RequestParam(required = false) String story,
                                       @RequestParam(required = false) String primaryImageUrl,
                                       @RequestParam(required = false) Long primaryMediaId,
                                       @RequestParam(defaultValue = "false") Boolean published,
                                       RedirectAttributes redirectAttributes) {
        try {
            contentManagerService.updateTalentProfile(id, displayName, category, headline, story, primaryImageUrl, published, primaryMediaId);
            redirectAttributes.addFlashAttribute("successMessage", "Talent profile updated.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/content/talent-showcase";
    }

    @PostMapping("/admin/content/talent-showcase/{id}/delete")
    public String deleteTalentShowcase(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        contentManagerService.deleteTalentProfile(id);
        redirectAttributes.addFlashAttribute("successMessage", "Talent profile deleted.");
        return "redirect:/admin/content/talent-showcase";
    }

    @GetMapping("/admin/content/media-library")
    public String contentMediaLibrary(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) String contentType,
                                      Model model) {
        model.addAttribute("adminPage", "media-library");
        model.addAttribute("pageTitle", "Media Library");
        model.addAttribute("items", contentManagerService.listMediaAssets(q, contentType));
        model.addAttribute("q", q);
        model.addAttribute("contentType", contentType);
        return "internal/content-manager/media-library";
    }

    @PostMapping("/admin/content/media-library")
    public String createMediaAsset(@RequestParam String storageKey,
                                   @RequestParam String publicUrl,
                                   @RequestParam(required = false) String contentType,
                                   @RequestParam(required = false) String title,
                                   @RequestParam(required = false) String altText,
                                   @RequestParam(required = false) Long fileSizeBytes,
                                   RedirectAttributes redirectAttributes) {
        try {
            contentManagerService.createMediaAsset(storageKey, publicUrl, contentType, title, altText, fileSizeBytes);
            redirectAttributes.addFlashAttribute("successMessage", "Media asset created.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/content/media-library";
    }

    @PostMapping("/admin/content/media-library/upload")
    public String uploadMediaAsset(@RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) String title,
                                   @RequestParam(required = false) String altText,
                                   RedirectAttributes redirectAttributes) {
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("successMessage", "No file selected.");
            return "redirect:/admin/content/media-library";
        }
        try {
            Path uploadRoot = Path.of(localUploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadRoot);
            String ext = "";
            String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            int dot = original.lastIndexOf('.');
            if (dot >= 0) {
                ext = original.substring(dot).toLowerCase(Locale.ROOT);
            }
            String storageKey = UUID.randomUUID() + ext;
            Path target = uploadRoot.resolve(storageKey).normalize();
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            String publicUrl = "/uploads/" + storageKey;
            contentManagerService.createMediaAsset(storageKey, publicUrl, file.getContentType(), title, altText, file.getSize());
            redirectAttributes.addFlashAttribute("successMessage", "Media uploaded.");
        } catch (IOException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("successMessage", ex.getMessage());
        }
        return "redirect:/admin/content/media-library";
    }

    @PostMapping("/admin/content/media-library/{id}/delete")
    public String deleteMediaAsset(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        contentManagerService.deleteMediaAsset(id);
        redirectAttributes.addFlashAttribute("successMessage", "Media asset deleted.");
        return "redirect:/admin/content/media-library";
    }

    private String handleFileUpload(org.springframework.web.multipart.MultipartFile file, String assetLabel) throws IOException {
        java.nio.file.Path uploadRoot = java.nio.file.Path.of(localUploadDir).toAbsolutePath().normalize();
        java.nio.file.Files.createDirectories(uploadRoot);
        String ext = "";
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot).toLowerCase(java.util.Locale.ROOT);
        }
        String storageKey = java.util.UUID.randomUUID() + ext;
        java.nio.file.Path target = uploadRoot.resolve(storageKey).normalize();
        java.nio.file.Files.copy(file.getInputStream(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        // Save asset in MediaLibrary for reference
        contentManagerService.createMediaAsset(storageKey, "/uploads/" + storageKey, file.getContentType(), original,
                assetLabel == null || assetLabel.isBlank() ? "Uploaded Image" : assetLabel, file.getSize());
        return "/uploads/" + storageKey;
    }

    private String normalizeOptionalImageUrl(String imageUrl) {
        if (imageUrl == null) {
            return null;
        }
        String normalized = imageUrl.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private List<String> collectAdditionalImageUrls(String additionalImageUrls, List<MultipartFile> additionalImageFiles,
                                                    String primaryImageUrl, String assetLabel) throws IOException {
        LinkedHashSet<String> imageUrls = new LinkedHashSet<>();
        if (additionalImageUrls != null) {
            for (String line : additionalImageUrls.split("\\r?\\n")) {
                String normalized = normalizeOptionalImageUrl(line);
                if (normalized != null) {
                    imageUrls.add(normalized);
                }
            }
        }
        if (additionalImageFiles != null) {
            for (MultipartFile file : additionalImageFiles) {
                if (file != null && !file.isEmpty()) {
                    imageUrls.add(handleFileUpload(file, assetLabel));
                }
            }
        }
        String normalizedPrimary = normalizeOptionalImageUrl(primaryImageUrl);
        if (normalizedPrimary != null) {
            imageUrls.remove(normalizedPrimary);
        }
        return new ArrayList<>(imageUrls);
    }
}
