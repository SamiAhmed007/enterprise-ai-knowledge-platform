package com.enterpriseai.knowledge.controller;

import com.enterpriseai.knowledge.config.SecurityConfig;
import com.enterpriseai.knowledge.domain.DocumentStatus;
import com.enterpriseai.knowledge.dto.AdminDtos.AnalyticsOverview;
import com.enterpriseai.knowledge.dto.AdminDtos.TokenUsage;
import com.enterpriseai.knowledge.security.JwtAuthenticationFilter;
import com.enterpriseai.knowledge.security.JwtService;
import com.enterpriseai.knowledge.security.PlatformUserDetailsService;
import com.enterpriseai.knowledge.security.AiRateLimitFilter;
import com.enterpriseai.knowledge.security.RateLimitService;
import com.enterpriseai.knowledge.security.RequestTracingFilter;
import com.enterpriseai.knowledge.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RequestTracingFilter.class,
        AiRateLimitFilter.class
})
class AdminControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private PlatformUserDetailsService userDetailsService;

    @MockBean
    private RateLimitService rateLimitService;

    @Test
    void anonymousUserCannotAccessAnalytics() throws Exception {
        mvc.perform(get("/api/admin/analytics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists(RequestTracingFilter.CORRELATION_HEADER));
    }

    @Test
    @WithMockUser(roles = "USER")
    void normalUserCannotAccessAnalytics() throws Exception {
        mvc.perform(get("/api/admin/analytics"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCanAccessAnalytics() throws Exception {
        when(adminService.analytics()).thenReturn(new AnalyticsOverview(
                3,
                4,
                5,
                Map.of(
                        DocumentStatus.UPLOADED, 0L,
                        DocumentStatus.PROCESSING, 1L,
                        DocumentStatus.READY, 3L,
                        DocumentStatus.FAILED, 1L),
                6,
                List.of(),
                List.of(),
                new TokenUsage(100, 50, 150, true)));

        mvc.perform(get("/api/admin/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(3))
                .andExpect(jsonPath("$.documentsByStatus.READY").value(3))
                .andExpect(jsonPath("$.tokenUsage.totalTokens").value(150));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCanAccessAllAdminDirectoryApis() throws Exception {
        when(adminService.users()).thenReturn(List.of());
        when(adminService.documents()).thenReturn(List.of());
        when(adminService.workspaces()).thenReturn(List.of());

        mvc.perform(get("/api/admin/users")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/documents")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/workspaces")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void normalUserCannotAccessAnyAdminDirectoryApi() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/documents")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/workspaces")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/stats")).andExpect(status().isForbidden());
    }
}
