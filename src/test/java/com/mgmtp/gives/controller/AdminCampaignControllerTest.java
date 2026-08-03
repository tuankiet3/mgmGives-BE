package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.config.SecurityConfig;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.mapper.AdminCampaignMapper;
import com.mgmtp.gives.mapper.CampaignMediaMapper;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.security.CustomUserDetailsService;
import com.mgmtp.gives.security.JwtAuthenticationEntryPoint;
import com.mgmtp.gives.security.JwtAuthenticationFilter;
import com.mgmtp.gives.security.JwtService;
import com.mgmtp.gives.security.OAuth2LoginSuccessHandler;
import com.mgmtp.gives.service.AdminCampaignService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCampaignController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class AdminCampaignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminCampaignService adminCampaignService;

    @MockitoBean
    private AdminCampaignMapper adminCampaignMapper;

    @MockitoBean
    private CampaignMediaMapper campaignMediaMapper;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private MailProps mailProps;

    private CustomUserDetails adminUserDetails;

    @BeforeEach
    void setUp() {
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@example.com");
        adminUser.setRole(UserRole.ADMIN);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUserDetails = new CustomUserDetails(adminUser);

        when(adminCampaignService.getCampaigns(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.<Campaign>emptyList()));
    }

    @Test
    void getCampaigns_BindsSingularCategoryIdParam() throws Exception {
        mockMvc.perform(get("/api/admin/campaigns")
                        .with(user(adminUserDetails))
                        .param("categoryId", "1"))
                .andExpect(status().isOk());

        assertCapturedCategoryIds(List.of(1L));
    }

    @Test
    void getCampaigns_BindsPluralCategoryIdsParam() throws Exception {
        mockMvc.perform(get("/api/admin/campaigns")
                        .with(user(adminUserDetails))
                        .param("categoryIds", "1"))
                .andExpect(status().isOk());

        assertCapturedCategoryIds(List.of(1L));
    }

    @Test
    void getCampaigns_MergesSingularAndPluralCategoryParams() throws Exception {
        mockMvc.perform(get("/api/admin/campaigns")
                        .with(user(adminUserDetails))
                        .param("categoryId", "1")
                        .param("categoryIds", "2"))
                .andExpect(status().isOk());

        assertCapturedCategoryIds(List.of(1L, 2L));
    }

    @Test
    void getCampaigns_BindsKeywordForTitleOrOrganizerSearch() throws Exception {
        mockMvc.perform(get("/api/admin/campaigns")
                        .with(user(adminUserDetails))
                        .param("keyword", "Thanh"))
                .andExpect(status().isOk());

        verify(adminCampaignService).getCampaigns(
                isNull(),
                any(),
                eq("Thanh"),
                any(Pageable.class));
    }

    @SuppressWarnings("unchecked")
    private void assertCapturedCategoryIds(List<Long> expectedCategoryIds) {
        ArgumentCaptor<List<Long>> categoryIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(adminCampaignService).getCampaigns(
                isNull(),
                categoryIdsCaptor.capture(),
                isNull(),
                any(Pageable.class));
        assertEquals(expectedCategoryIds, categoryIdsCaptor.getValue());
    }
}
