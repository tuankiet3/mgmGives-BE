package com.mgmtp.gives.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.config.SecurityConfig;
import com.mgmtp.gives.dto.announcement.AnnouncementReplyResponse;
import com.mgmtp.gives.dto.announcement.CreateReplyRequest;
import com.mgmtp.gives.dto.announcement.ReplyPageResponse;
import com.mgmtp.gives.dto.announcement.ReplyContextResponse;
import com.mgmtp.gives.dto.announcement.UpdateReplyRequest;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.security.*;
import com.mgmtp.gives.service.AnnouncementLikeService;
import com.mgmtp.gives.service.AnnouncementReplyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnnouncementEngagementController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class AnnouncementEngagementControllerTest {

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private MailProps mailProps;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AnnouncementLikeService likeService;

    @MockitoBean
    private AnnouncementReplyService replyService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private CustomUserDetails userDetails;
    private AnnouncementReplyResponse replyResponse;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(10L);
        user.setEmail("user@example.com");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        userDetails = new CustomUserDetails(user);

        replyResponse = new AnnouncementReplyResponse(
                100L,
                5L,
                "Test Content",
                new AnnouncementReplyResponse.UserSummary(10L, "User Name", null),
                false,
                LocalDateTime.now(),
                LocalDateTime.now(),
                new AnnouncementReplyResponse.ReplyReference(
                        99L,
                        new AnnouncementReplyResponse.UserSummary(11L, "Referenced User", null),
                        "Referenced content",
                        false
                )
        );
    }

    @Test
    void like_Success() throws Exception {
        doNothing().when(likeService).likeAnnouncement(eq(1L), eq(5L), any(User.class));

        mockMvc.perform(post("/api/campaigns/1/announcements/5/like")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Announcement liked successfully"));

        verify(likeService).likeAnnouncement(eq(1L), eq(5L), any(User.class));
    }

    @Test
    void unlike_Success() throws Exception {
        doNothing().when(likeService).unlikeAnnouncement(eq(1L), eq(5L), any(User.class));

        mockMvc.perform(delete("/api/campaigns/1/announcements/5/like")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Announcement unliked successfully"));

        verify(likeService).unlikeAnnouncement(eq(1L), eq(5L), any(User.class));
    }

    @Test
    void createReply_Success() throws Exception {
        CreateReplyRequest request = new CreateReplyRequest("Reply Content", 99L);
        when(replyService.createReply(eq(1L), eq(5L), any(CreateReplyRequest.class), any(User.class)))
                .thenReturn(replyResponse);

        mockMvc.perform(post("/api/campaigns/1/announcements/5/replies")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.id").value(100L))
                .andExpect(jsonPath("$.result.content").value("Test Content"))
                .andExpect(jsonPath("$.result.inReplyTo.id").value(99L))
                .andExpect(jsonPath("$.result.inReplyTo.createdBy.name").value("Referenced User"))
                .andExpect(jsonPath("$.result.inReplyTo.content").value("Referenced content"));
    }

    @Test
    void createReply_ValidationError() throws Exception {
        CreateReplyRequest request = new CreateReplyRequest(""); // Empty reply content

        mockMvc.perform(post("/api/campaigns/1/announcements/5/replies")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReply_NonPositiveReferencedReplyId_ValidationError() throws Exception {
        CreateReplyRequest request = new CreateReplyRequest("Reply Content", 0L);

        mockMvc.perform(post("/api/campaigns/1/announcements/5/replies")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReply_Success() throws Exception {
        UpdateReplyRequest request = new UpdateReplyRequest("Updated Content");
        when(replyService.updateReply(eq(1L), eq(5L), eq(100L), any(UpdateReplyRequest.class), any(User.class)))
                .thenReturn(replyResponse);

        mockMvc.perform(put("/api/campaigns/1/announcements/5/replies/100")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.id").value(100L));
    }

    @Test
    void deleteReply_Success() throws Exception {
        doNothing().when(replyService).deleteReply(eq(1L), eq(5L), eq(100L), any(User.class));

        mockMvc.perform(delete("/api/campaigns/1/announcements/5/replies/100")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Reply deleted successfully"));
    }

    @Test
    void getReplies_Success() throws Exception {
        ReplyPageResponse<AnnouncementReplyResponse> response = new ReplyPageResponse<>(List.of(replyResponse), null);
        when(replyService.getReplies(eq(1L), eq(5L), eq(null), eq(15), eq("desc"), any(User.class))).thenReturn(response);

        mockMvc.perform(get("/api/campaigns/1/announcements/5/replies")
                        .with(user(userDetails))
                        .param("limit", "15")
                        .param("sort", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.content[0].id").value(100L));
    }

    @Test
    void getReplies_MaxLimitExceeded_ValidationError() throws Exception {
        mockMvc.perform(get("/api/campaigns/1/announcements/5/replies")
                        .with(user(userDetails))
                        .param("limit", "100")) // Exceeds @Max(50)
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReplies_InvalidSort_ValidationError() throws Exception {
        mockMvc.perform(get("/api/campaigns/1/announcements/5/replies")
                        .with(user(userDetails))
                        .param("sort", "newest"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReplyContext_Success() throws Exception {
        ReplyContextResponse response = new ReplyContextResponse(List.of(replyResponse), 100L, 101L, 99L, true, true);
        when(replyService.getReplyContext(eq(1L), eq(5L), eq(100L), eq(null), eq(null), eq(15), eq("desc"), any(User.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/campaigns/1/announcements/5/replies/100/context")
                        .with(user(userDetails))
                        .param("limit", "15")
                        .param("sort", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.anchorReplyId").value(100L))
                .andExpect(jsonPath("$.result.content[0].inReplyTo.content").value("Referenced content"));
    }
}
