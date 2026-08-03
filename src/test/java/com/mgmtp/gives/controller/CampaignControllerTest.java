package com.mgmtp.gives.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.config.SecurityConfig;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.dto.campaign.CampaignRequest;
import com.mgmtp.gives.dto.campaign.CampaignResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.mapper.CampaignMapper;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.security.CustomUserDetailsService;
import com.mgmtp.gives.security.JwtAuthenticationFilter;
import com.mgmtp.gives.security.JwtService;
import com.mgmtp.gives.security.JwtAuthenticationEntryPoint;
import com.mgmtp.gives.security.OAuth2LoginSuccessHandler;
import com.mgmtp.gives.service.CampaignService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CampaignController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class })
class CampaignControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockitoBean
        private CampaignService campaignService;

        @MockitoBean
        private CampaignMapper campaignMapper;

        @MockitoBean
        private JwtService jwtService;

        @MockitoBean
        private CustomUserDetailsService customUserDetailsService;

        @MockitoBean
        private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

        @MockitoBean
        private MailProps mailProps;

        @MockitoBean
        private com.mgmtp.gives.repository.CampaignMediaRepository campaignMediaRepository;

        @MockitoBean
        private com.mgmtp.gives.mapper.CampaignMediaMapper campaignMediaMapper;

        private User regularUser;
        private User adminUser;
        private CustomUserDetails regularUserDetails;
        private CustomUserDetails adminUserDetails;
        private CampaignRequest validRequest;
        private CampaignResponse campaignResponse;

        @BeforeEach
        void setUp() {
                regularUser = new User();
                regularUser.setId(7L);
                regularUser.setEmail("user@example.com");
                regularUser.setFullName("Regular User");
                regularUser.setRole(UserRole.USER);
                regularUser.setStatus(UserStatus.ACTIVE);

                adminUser = new User();
                adminUser.setId(8L);
                adminUser.setEmail("admin@example.com");
                adminUser.setFullName("Admin User");
                adminUser.setRole(UserRole.ADMIN);
                adminUser.setStatus(UserStatus.ACTIVE);

                regularUserDetails = new CustomUserDetails(regularUser);
                adminUserDetails = new CustomUserDetails(adminUser);

                validRequest = new CampaignRequest(
                                "Kon Tum Water Project",
                                "Clean water for highland children",
                                new HashSet<>(List.of(1L, 2L)),
                                true,
                                true,
                                12000L,
                                LocalDateTime.now().plusDays(1),
                                LocalDateTime.now().plusDays(10),
                                CampaignPriority.HIGH,
                                CampaignStatus.PENDING);

                campaignResponse = CampaignResponse.builder()
                                .id(1L)
                                .title("Kon Tum Water Project")
                                .description("Clean water for highland children")
                                .status(CampaignStatus.PENDING)
                                .startDate(validRequest.startDate())
                                .endDate(validRequest.endDate())
                                .target(12000L)
                                .priority(CampaignPriority.HIGH)
                                .creatorId(7L)
                                .creatorName("Regular User")
                                .categories(Collections.emptyList())
                                .volunteersCount(5L)
                                .donorsCount(10L)
                                .build();

                lenient().when(campaignService.toResponse(any(), any())).thenReturn(campaignResponse);
                lenient().when(campaignService.toResponseList(any(), any())).thenReturn(List.of(campaignResponse));
        }

        @Test
        void createCampaign_Success_AuthenticatedUser() throws Exception {
                Campaign campaign = new Campaign();
                campaign.setId(1L);

                when(campaignService.createCampaign(any(CampaignRequest.class), any(User.class))).thenReturn(campaign);

                mockMvc.perform(post("/api/campaigns")
                                .with(user(regularUserDetails))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.message").value("Campaign created successfully"))
                                .andExpect(jsonPath("$.result.id").value(1))
                                .andExpect(jsonPath("$.result.title").value("Kon Tum Water Project"))
                                .andExpect(jsonPath("$.result.status").value("PENDING"))
                                .andExpect(jsonPath("$.result.creatorId").value(7));
        }

        @Test
        void createCampaign_Unauthorized_Anonymous() throws Exception {
                mockMvc.perform(post("/api/campaigns")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest)))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void createCampaign_ValidationError_BlankTitle() throws Exception {
                CampaignRequest invalidRequest = new CampaignRequest(
                                "   ", // Blank title
                                "Description",
                                new HashSet<>(List.of(1L)),
                                true,
                                true,
                                5000L,
                                LocalDateTime.now().plusDays(1),
                                LocalDateTime.now().plusDays(5),
                                CampaignPriority.NORMAL,
                                CampaignStatus.PENDING);

                mockMvc.perform(post("/api/campaigns")
                                .with(user(regularUserDetails))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.success").value(false))
                                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
        }

        @Test
        void getAllCampaigns_Success_Authenticated() throws Exception {
                Page<Campaign> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
                when(campaignService.getAllCampaigns(any(), any(), any(), any(), any(), any(), any(User.class),
                                any(Pageable.class)))
                                .thenReturn(page);

                mockMvc.perform(get("/api/campaigns")
                                .with(user(regularUserDetails))
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.result.content").isArray());
        }

        @Test
        void getAllCampaigns_WithIsFollowing_Success_Authenticated() throws Exception {
                Page<Campaign> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 9), 0);
                when(campaignService.getAllCampaigns(any(), any(), any(), any(), any(), eq(true), any(User.class),
                                any(Pageable.class)))
                                .thenReturn(page);

                mockMvc.perform(get("/api/campaigns")
                                .with(user(regularUserDetails))
                                .param("page", "0")
                                .param("size", "9")
                                .param("isFollowing", "true"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.result.content").isArray());
        }

        @Test
        void getAllCampaigns_Unauthorized_Anonymous() throws Exception {
                mockMvc.perform(get("/api/campaigns")
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void getCampaignById_Success_Authenticated() throws Exception {
                Campaign campaign = new Campaign();
                campaign.setId(1L);

                campaignResponse.setIsFollowed(true);
                campaignResponse.setIsJoined(true);
                campaignResponse.setVolunteersCount(5L);
                campaignResponse.setDonorsCount(10L);

                when(campaignService.getCampaignById(eq(1L), any())).thenReturn(campaign);
                when(campaignService.toResponse(eq(campaign), any())).thenReturn(campaignResponse);

                mockMvc.perform(get("/api/campaigns/1")
                                .with(user(regularUserDetails)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.result.id").value(1))
                                .andExpect(jsonPath("$.result.isFollowed").value(true))
                                .andExpect(jsonPath("$.result.isJoined").value(true))
                                .andExpect(jsonPath("$.result.volunteersCount").value(5))
                                .andExpect(jsonPath("$.result.donorsCount").value(10));
        }

        @Test
        void getCampaignById_Unauthorized_Anonymous() throws Exception {
                mockMvc.perform(get("/api/campaigns/1"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void updateCampaign_Success_Authenticated() throws Exception {
                Campaign campaign = new Campaign();
                campaign.setId(1L);

                when(campaignService.updateCampaign(eq(1L), any(CampaignRequest.class), any(User.class)))
                                .thenReturn(campaign);

                mockMvc.perform(put("/api/campaigns/1")
                                .with(user(regularUserDetails))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.result.id").value(1));
        }

        @Test
        void deleteCampaign_Success() throws Exception {
                doNothing().when(campaignService).deleteCampaign(eq(1L), any(User.class));

                mockMvc.perform(delete("/api/campaigns/1")
                                .with(user(regularUserDetails)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.message").value("Campaign deleted successfully"));
        }

        @Test
        void deleteCampaign_Unauthenticated_ReturnsUnauthorized() throws Exception {
                mockMvc.perform(delete("/api/campaigns/1"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void getCampaignById_Forbidden_WhenUnauthorizedAccess() throws Exception {
                when(campaignService.getCampaignById(eq(1L), any(User.class)))
                                .thenThrow(new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS,
                                                "You do not have permission to access this campaign"));

                mockMvc.perform(get("/api/campaigns/1")
                                .with(user(regularUserDetails)))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.success").value(false))
                                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS.getCode()))
                                .andExpect(jsonPath("$.message")
                                                .value("You do not have permission to access this campaign"));
        }
}
