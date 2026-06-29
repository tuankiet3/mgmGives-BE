package com.mgmtp.gives.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgmtp.gives.config.SecurityConfig;
import com.mgmtp.gives.dto.category.UserCategoryResponse;
import com.mgmtp.gives.dto.category.UserSuggestCategoryRequest;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.security.CustomUserDetailsService;
import com.mgmtp.gives.security.JwtAuthenticationFilter;
import com.mgmtp.gives.security.JwtService;
import com.mgmtp.gives.security.JwtAuthenticationEntryPoint;
import com.mgmtp.gives.service.UserCategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserCategoryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class UserCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserCategoryService userCategoryService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private CustomUserDetails regularUserDetails;
    private UserCategoryResponse categoryResponse;

    @BeforeEach
    void setUp() {
        User regularUser = new User();
        regularUser.setId(1L);
        regularUser.setEmail("user@example.com");
        regularUser.setFullName("Regular User");
        regularUser.setRole(UserRole.USER);
        regularUser.setStatus(UserStatus.ACTIVE);

        regularUserDetails = new CustomUserDetails(regularUser);

        categoryResponse = new UserCategoryResponse(1L, "Education", "School and study");
    }

    @Test
    void getApprovedCategories_Success_AuthenticatedUser() throws Exception {
        when(userCategoryService.getApprovedCategories()).thenReturn(List.of(categoryResponse));

        mockMvc.perform(get("/api/categories")
                        .with(user(regularUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result[0].id").value(1))
                .andExpect(jsonPath("$.result[0].name").value("Education"));
    }

    @Test
    void getApprovedCategories_Unauthorized_AnonymousUser() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suggestCategory_Success_AuthenticatedUser() throws Exception {
        UserSuggestCategoryRequest request = new UserSuggestCategoryRequest("New Category", "A valid description");
        when(userCategoryService.suggestCategory(any(UserSuggestCategoryRequest.class))).thenReturn(categoryResponse);

        mockMvc.perform(post("/api/categories/suggestions")
                        .with(user(regularUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.id").value(1));
    }

    @Test
    void suggestCategory_Unauthorized_AnonymousUser() throws Exception {
        UserSuggestCategoryRequest request = new UserSuggestCategoryRequest("New Category", "A valid description");

        mockMvc.perform(post("/api/categories/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suggestCategory_ValidationError_BlankName() throws Exception {
        UserSuggestCategoryRequest request = new UserSuggestCategoryRequest("  ", "A valid description");

        mockMvc.perform(post("/api/categories/suggestions")
                        .with(user(regularUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void suggestCategory_ValidationError_NameTooShort() throws Exception {
        UserSuggestCategoryRequest request = new UserSuggestCategoryRequest("A", "A valid description");

        mockMvc.perform(post("/api/categories/suggestions")
                        .with(user(regularUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
