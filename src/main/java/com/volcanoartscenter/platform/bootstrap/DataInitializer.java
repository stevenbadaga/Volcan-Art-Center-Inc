package com.volcanoartscenter.platform.bootstrap;

import com.volcanoartscenter.platform.shared.model.*;
import com.volcanoartscenter.platform.shared.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRepository productRepository;
    private final ExperienceRepository experienceRepository;
    private final BlogPostRepository blogPostRepository;
    private final ReviewRepository reviewRepository;
    private final ProductCollectionRepository productCollectionRepository;
    private final TestimonialRepository testimonialRepository;
    private final DonationCampaignRepository donationCampaignRepository;

    @Bean
    CommandLineRunner seedPlatformData(PasswordEncoder passwordEncoder) {
        return args -> {
            // ... (Roles and Users seeding remains the same)
            Role superAdminRole = roleRepository.findByName("SUPER_ADMIN")
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name("SUPER_ADMIN")
                            .description("Full platform control")
                            .build()));
            Role contentManagerRole = roleRepository.findByName("CONTENT_MANAGER")
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name("CONTENT_MANAGER")
                            .description("Catalog, media, and blog management")
                            .build()));
            Role opsManagerRole = roleRepository.findByName("OPS_MANAGER")
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name("OPS_MANAGER")
                            .description("Bookings, orders, and operations")
                            .build()));
            Role tourOperatorRole = roleRepository.findByName("TOUR_OPERATOR")
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name("TOUR_OPERATOR")
                            .description("B2B partner account")
                            .build()));
            Role registeredClientRole = roleRepository.findByName("REGISTERED_CLIENT")
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name("REGISTERED_CLIENT")
                            .description("Registered client account")
                            .build()));
            Role talentApplicantRole = roleRepository.findByName("TALENT_APPLICANT")
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name("TALENT_APPLICANT")
                            .description("Talent applicant account")
                            .build()));

            createOrUpdateSeedUser("admin1@volcanoartscenter.rw", "Platform", "Admin",
                    Set.of(superAdminRole), "SuperAdmin!2026", passwordEncoder);
            createOrUpdateSeedUser("admin2@volcanoartscenter.rw", "Operations", "Admin",
                    Set.of(opsManagerRole), "OpsManager!2026", passwordEncoder);
            createOrUpdateSeedUser("admin3@volcanoartscenter.rw", "Content", "Admin",
                    Set.of(contentManagerRole), "ContentManager!2026", passwordEncoder);

            createOrUpdateSeedUser("client1@volcanoartscenter.rw", "Registered", "Client",
                    Set.of(registeredClientRole), "RegisteredClient!2026", passwordEncoder);
            createOrUpdateSeedUser("operator1@volcanoartscenter.rw", "Tour", "Operator",
                    Set.of(tourOperatorRole), "TourOperator!2026", passwordEncoder);

            if (productCollectionRepository.count() == 0) {
                productCollectionRepository.save(ProductCollection.builder()
                        .name("Signature Masterpieces")
                        .slug("signature-masterpieces")
                        .description("Hand-picked premium artworks from our most celebrated local artists.")
                        .displayOrder(1)
                        .active(true)
                        .build());
            }

            ProductCategory visual = category("visual-arts", "Visual Arts Collection",
                    "Original Rwandan artworks including paintings, drawings, and sculptures.", 1);
            ProductCategory crafts = category("handicrafts", "Handicrafts & Cultural Crafts",
                    "Handmade baskets, textiles, artifacts, and artisan products.", 2);
            ProductCategory cultural = category("cultural-experiences", "Cultural Experience Packages",
                    "Immersive cultural tourism experiences and curated programs.", 3);

            if (productRepository.count() == 0) {
                Product p1 = productRepository.save(Product.builder()
                        .name("Musanze Sunrise Painting")
                        .slug("musanze-sunrise-painting")
                        .description("One-of-a-kind painting inspired by the mountain sunrise near Volcanoes National Park.")
                        .shortDescription("Unique original painting by a local artist.")
                        .price(new BigDecimal("620.00"))
                        .inventoryType(Product.InventoryType.UNIQUE)
                        .stockQuantity(1)
                        .available(true)
                        .featured(true)
                        .artistName("Aline Uwimana")
                        .medium("Acrylic on canvas")
                        .dimensions("90cm x 60cm")
                        .primaryImageUrl("/uploads/arts.png")
                        .category(visual)
                        .shippingNote("International shipping available via FedEx.")
                        .build());

                productRepository.save(Product.builder()
                        .name("Traditional Agaseke Basket")
                        .slug("traditional-agaseke-basket")
                        .description("Handcrafted traditional Rwandan basket produced by local women artisans.")
                        .shortDescription("Handcrafted basket made by local cooperatives.")
                        .price(new BigDecimal("48.00"))
                        .inventoryType(Product.InventoryType.BATCH)
                        .stockQuantity(35)
                        .available(true)
                        .featured(true)
                        .artistName("Volcano Artisan Cooperative")
                        .medium("Natural fibers")
                        .dimensions("35cm x 35cm")
                        .primaryImageUrl("/uploads/arts page.jpg")
                        .category(crafts)
                        .shippingNote("FedEx international shipping and local Rwanda delivery available.")
                        .build());

                productRepository.save(Product.builder()
                        .name("Authentic Cultural Welcome Package")
                        .slug("authentic-cultural-welcome-package")
                        .description("A curated package for visitors combining welcome dance, storytelling, and art interaction.")
                        .shortDescription("Cultural welcome experience package.")
                        .price(new BigDecimal("120.00"))
                        .inventoryType(Product.InventoryType.BATCH)
                        .stockQuantity(100)
                        .available(true)
                        .featured(false)
                        .artistName("Volcano Arts Center Team")
                        .primaryImageUrl("/uploads/Arts Page 2.jpg")
                        .category(cultural)
                        .shippable(false)
                        .build());

                productCollectionRepository.findBySlug("signature-masterpieces").ifPresent(coll -> {
                    p1.setCollection(coll);
                    productRepository.save(p1);
                });
            }

            if (experienceRepository.count() == 0) {
                experienceRepository.save(Experience.builder()
                        .title("Traditional Dance & Cultural Performance")
                        .slug("traditional-dance-cultural-performance")
                        .description("Authentic performance and guided cultural interpretation by trained community performers.")
                        .shortDescription("Interactive traditional dance and storytelling.")
                        .experienceType(Experience.ExperienceType.CULTURAL)
                        .bookingType(Experience.BookingType.DIRECT)
                        .pricePerPerson(new BigDecimal("65.00"))
                        .groupPrice(new BigDecimal("480.00"))
                        .priceNote("Flexible pricing for tour operators and larger groups.")
                        .minGroupSize(1)
                        .maxGroupSize(30)
                        .durationHours(new BigDecimal("2.5"))
                        .location("Volcano Arts Center Cultural Hub, Musanze")
                        .meetingPoint("Volcano Arts Center Reception")
                        .languagesOffered("English, French")
                        .availableDaily(true)
                        .featured(true)
                        .active(true)
                        .primaryImageUrl("/uploads/media page.jpg")
                        .build());

                experienceRepository.save(Experience.builder()
                        .title("Village Walk & Community Life Experience")
                        .slug("village-walk-community-life")
                        .description("Guided immersion through nearby villages with daily-life participation and local storytelling.")
                        .shortDescription("Village walk, community interaction, and rural lifestyle immersion.")
                        .experienceType(Experience.ExperienceType.VILLAGE)
                        .bookingType(Experience.BookingType.INQUIRY)
                        .pricePerPerson(new BigDecimal("85.00"))
                        .groupPrice(new BigDecimal("650.00"))
                        .minGroupSize(2)
                        .maxGroupSize(15)
                        .durationHours(new BigDecimal("4.0"))
                        .location("Communities near Volcanoes National Park")
                        .meetingPoint("Volcano Arts Center Reception")
                        .languagesOffered("English, French")
                        .availableDaily(true)
                        .featured(true)
                        .active(true)
                        .primaryImageUrl("/uploads/talent and media.jpg")
                        .build());

                experienceRepository.save(Experience.builder()
                        .title("Community Conservation Experience")
                        .slug("community-conservation-experience")
                        .description("Join eco-conscious cultural activities including conservation awareness, nursery preparation, and tree planting.")
                        .shortDescription("Conservation-focused community engagement activity.")
                        .experienceType(Experience.ExperienceType.CONSERVATION)
                        .bookingType(Experience.BookingType.INQUIRY)
                        .pricePerPerson(new BigDecimal("50.00"))
                        .groupPrice(new BigDecimal("360.00"))
                        .minGroupSize(2)
                        .maxGroupSize(20)
                        .durationHours(new BigDecimal("3.0"))
                        .location("Musanze communities")
                        .meetingPoint("Volcano Arts Center Reception")
                        .languagesOffered("English, French")
                        .availableDaily(false)
                        .availableDays("MON,WED,FRI,SAT")
                        .featured(false)
                        .active(true)
                        .primaryImageUrl("/uploads/Media and dance 2.jpg")
                        .build());
            }

            if (blogPostRepository.count() == 0) {
                blogPostRepository.save(BlogPost.builder()
                        .title("Welcome to Volcano Arts Center Digital Platform")
                        .slug("welcome-to-volcano-arts-center-digital-platform")
                        .excerpt("A new digital gateway to authentic art, culture, and community-based tourism in Rwanda.")
                        .content("This platform connects international visitors, tour operators, and local communities through immersive experiences, art discovery, and meaningful partnerships.")
                        .category(BlogPost.BlogCategory.UPDATE)
                        .authorDisplayName("Volcano Arts Center Team")
                        .published(true)
                        .publishedAt(LocalDateTime.now().minusDays(3))
                        .featuredImageUrl("/uploads/conservation.png")
                        .build());

                blogPostRepository.save(BlogPost.builder()
                        .title("How Community Tourism Creates Local Impact")
                        .slug("how-community-tourism-creates-local-impact")
                        .excerpt("Tourism can preserve culture while creating sustainable income for women, youth, and artisans.")
                        .content("At Volcano Arts Center, every experience is designed to connect visitors with people, stories, and grassroots impact.")
                        .category(BlogPost.BlogCategory.STORY)
                        .authorDisplayName("Volcano Arts Center Team")
                        .published(true)
                        .publishedAt(LocalDateTime.now().minusDays(1))
                        .featuredImageUrl("/uploads/conservation2.png")
                        .build());
            }

            if (testimonialRepository.count() == 0) {
                testimonialRepository.save(Testimonial.builder()
                        .authorName("Sophie Martin")
                        .authorCountry("France")
                        .authorTitle("Eco-Traveler")
                        .message("The traditional dance performance was breathtaking. A truly authentic connection with Rwandan culture.")
                        .rating(5)
                        .published(true)
                        .featured(true)
                        .build());
                testimonialRepository.save(Testimonial.builder()
                        .authorName("James Wilson")
                        .authorCountry("USA")
                        .authorTitle("Art Collector")
                        .message("The quality of the paintings and the stories behind them are remarkable. Fast shipping too!")
                        .rating(5)
                        .published(true)
                        .featured(true)
                        .build());
            }

            if (donationCampaignRepository.count() == 0) {
                donationCampaignRepository.save(DonationCampaign.builder()
                        .name("Community Reforestation Initiative")
                        .description("Supporting local families to plant indigenous trees in the Volcanoes National Park buffer zone.")
                        .impactStatement("$10 plants 3 trees and supports a local nursery worker.")
                        .impactTiers("{\"10\":\"Plants 3 trees\",\"50\":\"Funds a nursery for a week\",\"100\":\"Reforests a small plot\"}")
                        .goalAmount(new BigDecimal("5000.00"))
                        .raisedAmount(new BigDecimal("1250.00"))
                        .donorCount(42)
                        .active(true)
                        .imageUrl("/uploads/conservation.png")
                        .build());
            }

            if (reviewRepository.count() == 0 && !productRepository.findByAvailableTrueOrderByFeaturedDescNameAsc().isEmpty()) {
                Product sampleProduct = productRepository.findByAvailableTrueOrderByFeaturedDescNameAsc().get(0);
                reviewRepository.save(Review.builder()
                        .reviewerName("Sophie M.")
                        .reviewerEmail("sophie@example.com")
                        .reviewerCountry("France")
                        .rating(5)
                        .comment("An unforgettable cultural experience and excellent artisan craftsmanship.")
                        .approved(true)
                        .featured(true)
                        .product(sampleProduct)
                        .build());
            }
        };
    }

    private ProductCategory category(String slug, String name, String description, int order) {
        return productCategoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                .filter(cat -> slug.equals(cat.getSlug()))
                .findFirst()
                .orElseGet(() -> productCategoryRepository.save(ProductCategory.builder()
                        .slug(slug)
                        .name(name)
                        .description(description)
                        .displayOrder(order)
                        .active(true)
                        .build()));
    }

    private void createOrUpdateSeedUser(String email, String firstName, String lastName, Set<Role> roles, String rawPassword, PasswordEncoder encoder) {
        User user = userRepository.findByEmail(email).orElseGet(() -> User.builder()
                .email(email)
                .build());
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPassword(encoder.encode(rawPassword));
        user.setEnabled(true);
        user.setRoles(roles);
        userRepository.save(user);
    }
}
