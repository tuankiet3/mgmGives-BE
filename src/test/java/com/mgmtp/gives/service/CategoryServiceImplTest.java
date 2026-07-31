package com.mgmtp.gives.service;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.category.*;
import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.mapper.CategoryMapper;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.CategoryRepository;
import com.mgmtp.gives.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private CampaignRepository campaignRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private Category testCategory;
    private UserCategoryResponse userCategoryResponse;
    private AdminCategoryResponse adminCategoryResponse;

    @BeforeEach
    void setUp() {
        testCategory = new Category();
        testCategory.setId(1L);
        testCategory.setName("Education");
        testCategory.setDescription("Schooling and training");
        testCategory.setDeletedAt(null);

        userCategoryResponse = new UserCategoryResponse(1L, "Education", "Schooling and training");
        adminCategoryResponse = new AdminCategoryResponse(1L, "Education", "Schooling and training", null, 0L);
    }

    @Test
    void getApprovedCategories_Success() {
        List<Category> categories = List.of(testCategory);
        when(categoryRepository.findAllByDeletedAtIsNullOrderByNameAsc()).thenReturn(categories);
        when(categoryMapper.toUserResponseList(categories)).thenReturn(List.of(userCategoryResponse));

        List<UserCategoryResponse> result = categoryService.getApprovedCategories();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Education", result.getFirst().name());
        verify(categoryRepository, times(1)).findAllByDeletedAtIsNullOrderByNameAsc();
    }

    @Test
    void createCategory_Success() {
        AdminCreateCategoryRequest request = new AdminCreateCategoryRequest("Health", "Wellness");
        Category mappedCategory = new Category();
        mappedCategory.setName("Health");
        mappedCategory.setDescription("Wellness");

        Category savedCategory = new Category();
        savedCategory.setId(3L);
        savedCategory.setName("Health");
        savedCategory.setDescription("Wellness");

        AdminCategoryResponse responseDto = new AdminCategoryResponse(3L, "Health", "Wellness", null, 0L);

        when(categoryMapper.toEntity(any(AdminCreateCategoryRequest.class))).thenReturn(mappedCategory);
        when(categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Health")).thenReturn(false);
        when(categoryRepository.findByNameIgnoreCase("Health")).thenReturn(Optional.empty());
        when(categoryRepository.save(mappedCategory)).thenReturn(savedCategory);
        when(categoryMapper.toAdminResponse(savedCategory)).thenReturn(responseDto);

        AdminCategoryResponse result = categoryService.createCategory(request);

        assertNotNull(result);
        assertEquals("Health", result.name());
        verify(categoryRepository, times(1)).existsByNameIgnoreCaseAndDeletedAtIsNull("Health");
        verify(categoryRepository, times(1)).save(mappedCategory);
    }

    @Test
    void createCategory_DuplicateName_ThrowsException() {
        AdminCreateCategoryRequest request = new AdminCreateCategoryRequest("Education", "Description");

        when(categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Education")).thenReturn(true);

        AppException exception = assertThrows(AppException.class, () ->
                categoryService.createCategory(request)
        );

        assertEquals(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS, exception.getErrorCode());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAllCategories_Success() {
        Page<Category> categoryPage = new PageImpl<>(List.of(testCategory));
        when(categoryRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(categoryPage);
        when(categoryMapper.toAdminResponse(testCategory)).thenReturn(adminCategoryResponse);

        Page<AdminCategoryResponse> result = categoryService.getAllCategories(
                false,
                "keyword",
                PageRequest.of(0, 10)
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Education", result.getContent().getFirst().name());
        verify(categoryRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getCategoryById_Success() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(categoryMapper.toAdminResponse(testCategory)).thenReturn(adminCategoryResponse);

        AdminCategoryResponse result = categoryService.getCategoryById(1L);

        assertNotNull(result);
        assertEquals("Education", result.name());
    }

    @Test
    void getCategoryById_NotFound_ThrowsException() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                categoryService.getCategoryById(99L)
        );

        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updateCategory_Success() {
        AdminUpdateCategoryRequest updateRequest = new AdminUpdateCategoryRequest("Renamed Education", "New Description");

        Category updatedCategory = new Category();
        updatedCategory.setId(1L);
        updatedCategory.setName("Renamed Education");
        updatedCategory.setDescription("New Description");

        AdminCategoryResponse updatedResponse = new AdminCategoryResponse(1L, "Renamed Education", "New Description", null, 0L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Renamed Education")).thenReturn(false);
        when(categoryRepository.findByNameIgnoreCase("Renamed Education")).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            Category target = invocation.getArgument(1);
            target.setName("Renamed Education");
            target.setDescription("New Description");
            return null;
        }).when(categoryMapper).updateEntityFromRequest(any(AdminUpdateCategoryRequest.class), any(Category.class));
        when(categoryRepository.save(any(Category.class))).thenReturn(updatedCategory);
        when(categoryMapper.toAdminResponse(updatedCategory)).thenReturn(updatedResponse);

        AdminCategoryResponse result = categoryService.updateCategory(1L, updateRequest);

        assertNotNull(result);
        assertEquals("Renamed Education", result.name());
        verify(categoryRepository, times(1)).save(any(Category.class));
    }

    @Test
    void updateCategory_DuplicateName_ThrowsException() {
        AdminUpdateCategoryRequest updateRequest = new AdminUpdateCategoryRequest("Duplicate Name", "New Description");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Duplicate Name")).thenReturn(true);

        AppException exception = assertThrows(AppException.class, () ->
                categoryService.updateCategory(1L, updateRequest)
        );

        assertEquals(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS, exception.getErrorCode());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void deleteCategory_Success() {
        when(categoryRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        categoryService.deleteCategory(1L);

        assertNotNull(testCategory.getDeletedAt());
        verify(categoryRepository, times(1)).save(testCategory);
    }

    @Test
    void restoreCategory_Success() {
        testCategory.setDeletedAt(LocalDateTime.now());
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Education")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryMapper.toAdminResponse(testCategory)).thenReturn(adminCategoryResponse);

        AdminCategoryResponse result = categoryService.restoreCategory(1L);

        assertNotNull(result);
        assertNull(testCategory.getDeletedAt());
        verify(categoryRepository, times(1)).save(testCategory);
    }

    @Test
    void checkCategoryDeletion_Success() {
        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(campaignRepository.countCampaignsByCategoryId(1L)).thenReturn(5L);
        when(campaignRepository.countCampaignsWhereCategoryIsOnlyOne(1L)).thenReturn(2L);

        CategoryDeleteCheckResponse result = categoryService.checkCategoryDeletion(1L);

        assertNotNull(result);
        assertEquals(5L, result.assignedCampaignsCount());
        assertEquals(2L, result.onlyCategoryCampaignsCount());
    }

    @Test
    void createCategory_SoftDeletedExists_ThrowsCategoryAlreadyExistsButDeletedException() {
        AdminCreateCategoryRequest request = new AdminCreateCategoryRequest("Health", "Wellness");
        Category softDeletedCategory = new Category();
        softDeletedCategory.setId(10L);
        softDeletedCategory.setName("Health");
        softDeletedCategory.setDeletedAt(LocalDateTime.now());

        when(categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Health")).thenReturn(false);
        when(categoryRepository.findByNameIgnoreCase("Health")).thenReturn(Optional.of(softDeletedCategory));

        AppException exception = assertThrows(AppException.class, () ->
                categoryService.createCategory(request)
        );

        assertEquals(ErrorCode.CATEGORY_ALREADY_EXISTS_BUT_DELETED, exception.getErrorCode());
        java.util.Map<?, ?> result = (java.util.Map<?, ?>) exception.getResult();
        assertNotNull(result);
        assertEquals(10L, result.get("id"));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void updateCategory_SoftDeletedExists_ThrowsCategoryAlreadyExistsButDeletedException() {
        AdminUpdateCategoryRequest updateRequest = new AdminUpdateCategoryRequest("Renamed Health", "Description");
        Category softDeletedCategory = new Category();
        softDeletedCategory.setId(10L);
        softDeletedCategory.setName("Renamed Health");
        softDeletedCategory.setDeletedAt(LocalDateTime.now());

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Renamed Health")).thenReturn(false);
        when(categoryRepository.findByNameIgnoreCase("Renamed Health")).thenReturn(Optional.of(softDeletedCategory));

        AppException exception = assertThrows(AppException.class, () ->
                categoryService.updateCategory(1L, updateRequest)
        );

        assertEquals(ErrorCode.CATEGORY_ALREADY_EXISTS_BUT_DELETED, exception.getErrorCode());
        java.util.Map<?, ?> result = (java.util.Map<?, ?>) exception.getResult();
        assertNotNull(result);
        assertEquals(10L, result.get("id"));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void restoreCategory_ActiveNameExists_ThrowsCategoryNameAlreadyExistsException() {
        testCategory.setDeletedAt(LocalDateTime.now());
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Education")).thenReturn(true);

        AppException exception = assertThrows(AppException.class, () ->
                categoryService.restoreCategory(1L)
        );

        assertEquals(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS, exception.getErrorCode());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void permanentDeleteCategory_Success() {
        testCategory.setDeletedAt(LocalDateTime.now());
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(campaignRepository.countCampaignsByCategoryId(1L)).thenReturn(0L);
        doNothing().when(categoryRepository).delete(testCategory);

        categoryService.permanentDeleteCategory(1L);

        verify(categoryRepository, times(1)).delete(testCategory);
    }

    @Test
    void permanentDeleteCategory_NotFound_ThrowsException() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                categoryService.permanentDeleteCategory(99L)
        );

        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, exception.getErrorCode());
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void permanentDeleteCategory_NotArchived_ThrowsException() {
        testCategory.setDeletedAt(null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));

        AppException exception = assertThrows(AppException.class, () ->
                categoryService.permanentDeleteCategory(1L)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void permanentDeleteCategory_AssignedToCampaigns_ThrowsException() {
        testCategory.setDeletedAt(LocalDateTime.now());
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(campaignRepository.countCampaignsByCategoryId(1L)).thenReturn(3L);

        AppException exception = assertThrows(AppException.class, () ->
                categoryService.permanentDeleteCategory(1L)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(categoryRepository, never()).delete(any(Category.class));
    }
}
