package com.mgmtp.gives.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.config.SecurityConfig;
import com.mgmtp.gives.dto.category.AdminCategoryResponse;
import com.mgmtp.gives.dto.category.AdminCreateCategoryRequest;
import com.mgmtp.gives.dto.category.AdminUpdateCategoryRequest;
import com.mgmtp.gives.dto.category.CategoryDeleteCheckResponse;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.security.*;
import com.mgmtp.gives.service.AdminCategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCategoryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class AdminCategoryControllerTest {

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private MailProps mailProps;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminCategoryService adminCategoryService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private CustomUserDetails adminUserDetails;
    private CustomUserDetails regularUserDetails;
    private AdminCategoryResponse adminCategoryResponse;

    @BeforeEach
    void setUp() {
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@example.com");
        adminUser.setRole(UserRole.ADMIN);
        adminUser.setStatus(UserStatus.ACTIVE);

        User regularUser = new User();
        regularUser.setId(2L);
        regularUser.setEmail("user@example.com");
        regularUser.setRole(UserRole.USER);
        regularUser.setStatus(UserStatus.ACTIVE);

        adminUserDetails = new CustomUserDetails(adminUser);
        regularUserDetails = new CustomUserDetails(regularUser);

        adminCategoryResponse = new AdminCategoryResponse(1L, "Education", "Schooling", null, 0L);
    }

    @Test
    void createCategory_Success_Admin() throws Exception {
        AdminCreateCategoryRequest request = new AdminCreateCategoryRequest("Education", "Schooling");
        when(adminCategoryService.createCategory(any(AdminCreateCategoryRequest.class))).thenReturn(adminCategoryResponse);

        mockMvc.perform(post("/api/admin/categories")
                        .with(user(adminUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Category Created Successfully"))
                .andExpect(jsonPath("$.result.id").value(1));
    }

    @Test
    void createCategory_Forbidden_RegularUser() throws Exception {
        AdminCreateCategoryRequest request = new AdminCreateCategoryRequest("Education", "Schooling");

        mockMvc.perform(post("/api/admin/categories")
                        .with(user(regularUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllCategories_Success_Admin() throws Exception {
        Page<AdminCategoryResponse> page = new PageImpl<>(List.of(adminCategoryResponse));
        when(adminCategoryService.getAllCategories(eq(false), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/admin/categories")
                        .with(user(adminUserDetails))
                        .param("showDeleted", "false")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.content[0].name").value("Education"));
    }

    @Test
    void getCategoryById_Success_Admin() throws Exception {
        when(adminCategoryService.getCategoryById(1L)).thenReturn(adminCategoryResponse);

        mockMvc.perform(get("/api/admin/categories/1")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.id").value(1));
    }

    @Test
    void updateCategory_Success_Admin() throws Exception {
        AdminUpdateCategoryRequest request = new AdminUpdateCategoryRequest("Updated Education", "New Schooling");
        AdminCategoryResponse updatedResponse = new AdminCategoryResponse(1L, "Updated Education", "New Schooling", null, 0L);
        when(adminCategoryService.updateCategory(eq(1L), any(AdminUpdateCategoryRequest.class))).thenReturn(updatedResponse);

        mockMvc.perform(put("/api/admin/categories/1")
                        .with(user(adminUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.name").value("Updated Education"));
    }

    @Test
    void deleteCategory_Success_Admin() throws Exception {
        doNothing().when(adminCategoryService).deleteCategory(1L);

        mockMvc.perform(delete("/api/admin/categories/1")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void restoreCategory_Success_Admin() throws Exception {
        when(adminCategoryService.restoreCategory(1L)).thenReturn(adminCategoryResponse);

        mockMvc.perform(post("/api/admin/categories/1/restore")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Category Restored Successfully"));
    }

    @Test
    void checkCategoryDeletion_Success_Admin() throws Exception {
        CategoryDeleteCheckResponse response = new CategoryDeleteCheckResponse(5, 2);
        when(adminCategoryService.checkCategoryDeletion(1L)).thenReturn(response);

        mockMvc.perform(get("/api/admin/categories/1/delete-check")
                        .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.assignedCampaignsCount").value(5))
                .andExpect(jsonPath("$.result.onlyCategoryCampaignsCount").value(2));
    }
}
