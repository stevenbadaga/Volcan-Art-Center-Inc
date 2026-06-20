package com.volcanoartscenter.platform.web;

import com.volcanoartscenter.platform.shared.model.Booking;
import com.volcanoartscenter.platform.shared.model.Experience;
import com.volcanoartscenter.platform.shared.model.Role;
import com.volcanoartscenter.platform.shared.model.User;
import com.volcanoartscenter.platform.shared.repository.BookingRepository;
import com.volcanoartscenter.platform.shared.repository.ExperienceRepository;
import com.volcanoartscenter.platform.shared.repository.RoleRepository;
import com.volcanoartscenter.platform.shared.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientBookingsRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ExperienceRepository experienceRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User clientUser;
    private Booking booking;

    @BeforeEach
    void ensureClientBookingFixture() {
        Role clientRole = roleRepository.findByName("REGISTERED_CLIENT")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("REGISTERED_CLIENT")
                        .description("Registered client")
                        .build()));

        clientUser = userRepository.findByEmail("client-bookings@test.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .email("client-bookings@test.com")
                        .firstName("Client")
                        .lastName("Bookings")
                        .password(passwordEncoder.encode("Secret123!"))
                        .enabled(true)
                        .roles(Set.of(clientRole))
                        .build()));

        String unique = UUID.randomUUID().toString().substring(0, 8).toLowerCase();
        Experience experience = experienceRepository.save(Experience.builder()
                .title("Bookings Render Experience " + unique)
                .slug("bookings-render-experience-" + unique)
                .shortDescription("Testing client booking render routes")
                .description("Testing client booking render routes")
                .pricePerPerson(new BigDecimal("250.00"))
                .experienceType(Experience.ExperienceType.CULTURAL)
                .bookingType(Experience.BookingType.DIRECT)
                .minGroupSize(1)
                .maxGroupSize(12)
                .active(true)
                .featured(false)
                .build());

        booking = bookingRepository.save(Booking.builder()
                .bookingReference("BOOK-" + unique.toUpperCase())
                .user(clientUser)
                .experience(experience)
                .guestName("Client Bookings")
                .guestEmail("client-bookings@test.com")
                .guestPhone("+250700000000")
                .guestCountry("Rwanda")
                .preferredDate(LocalDate.now().plusDays(10))
                .alternativeDate(LocalDate.now().plusDays(12))
                .groupSize(3)
                .preferredLanguage("English")
                .specialRequests("Vegetarian lunch only")
                .status(Booking.BookingStatus.CONFIRMED)
                .totalPrice(new BigDecimal("750.00"))
                .depositAmount(new BigDecimal("375.00"))
                .depositRequired(true)
                .paymentMethod("PAY_LATER")
                .paymentStatus(Booking.PaymentStatus.PAID)
                .paymentReference("PAY-" + unique.toUpperCase())
                .tourOperatorName("Direct client booking")
                .tourOperatorEmail("operations@volcanoartscenter.rw")
                .adminNotes("Bring guest passes to check-in")
                .paymentDueAt(LocalDateTime.now().plusDays(2))
                .confirmedAt(LocalDateTime.now())
                .build());
    }

    @Test
    void clientBookingsPageRendersStandaloneView() throws Exception {
        mockMvc.perform(get("/client/bookings")
                        .with(user(clientUser.getEmail()).roles("REGISTERED_CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("My Bookings")))
                .andExpect(content().string(containsString(booking.getBookingReference())))
                .andExpect(content().string(containsString("Open full details")));
    }

    @Test
    void clientBookingDetailPageRendersFullBookingRecord() throws Exception {
        mockMvc.perform(get("/client/bookings/" + booking.getBookingReference())
                        .with(user(clientUser.getEmail()).roles("REGISTERED_CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Booking details")))
                .andExpect(content().string(containsString("Vegetarian lunch only")))
                .andExpect(content().string(containsString("Bring guest passes to check-in")))
                .andExpect(content().string(containsString("operations@volcanoartscenter.rw")))
                .andExpect(content().string(containsString(booking.getPaymentReference())));
    }
}
