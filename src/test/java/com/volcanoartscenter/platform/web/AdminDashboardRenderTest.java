package com.volcanoartscenter.platform.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDashboardRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminDashboardRendersFullShellForSuperAdmin() throws Exception {
        var result = mockMvc.perform(get("/admin/dashboard")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin1@volcanoartscenter.rw").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SUPER ADMIN COMMAND CENTER")))
                .andExpect(content().string(containsString("Volcano Arts")))
                .andExpect(content().string(containsString("Manage Staff")))
                .andReturn();

        Files.writeString(
                Path.of("target", "admin-dashboard-render.html"),
                result.getResponse().getContentAsString(),
                StandardCharsets.UTF_8
        );
    }
}
