package com.mgmtp.gives.service;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.category.*;
import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.enums.CategoryStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.mapper.CategoryMapper;
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

import java.util.Collections;
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
        testCategory.setStatus(CategoryStatus.APPROVED);

        userCategoryResponse = new UserCategoryResponse(1L, "Education", "Schooling and training");
        adminCategoryResponse = new AdminCategoryResponse(1L, "Education", "Schooling and training", CategoryStatus.APPROVED);
    }

    @Test
    void getApprovedCategories_Success() {
        List<Category> categories = List.of(testCategory);
        when(categoryRepository.findAllByStatusOrderByNameAsc(CategoryStatus.APPROVED)).thenReturn(categories);
        when(categoryMapper.toUserResponseList(categories)).thenReturn(List.of(userCategoryResponse));

        List<UserCategoryResponse> result = categoryService.getApprovedCategories();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Education", result.getFirst().name());
        verify(categoryRepository, times(1)).findAllByStatusOrderByNameAsc(CategoryStatus.APPROVED);
    }

    @Test
    void suggestCategory_Success() {
        UserSuggestCategoryRequest request = new UserSuggestCategoryRequest("  Health & Wellness ", "Medical suggestions");
        Category mappedCategory = new Category();
        mappedCategory.setName("Health & Wellness");
        mappedCategory.setDescription("Medical suggestions");
        mappedCategory.setStatus(CategoryStatus.PENDING);

        Category savedCategory = new Category();
        savedCategory.setId(2L);
        savedCategory.setName("Health & Wellness");
        savedCategory.setDescription("Medical suggestions");
        savedCategory.setStatus(CategoryStatus.PENDING);

        UserCategoryResponse responseDto = new UserCategoryResponse(2L, "Health & Wellness", "Medical suggestions");

        when(categoryRepository.findByNameIgnoreCase("Health & Wellness")).thenReturn(Optional.empty());
        when(categoryMapper.toEntity(any(UserSuggestCategoryRequest.class))).thenReturn(mappedCategory);
        when(categoryRepository.save(mappedCategory)).thenReturn(savedCategory);
        when(categoryMapper.toUserResponse(savedCategory)).thenReturn(responseDto);

        UserCategoryResponse result = categoryService.suggestCategory(request);

        assertNotNull(result);
        assertEquals("Health & Wellness", result.name());
        verify(categoryRepository, times(1)).findByNameIgnoreCase("Health & Wellness");
        verify(categoryRepository, times(1)).save(mappedCategory);
    }

    @Test
    void suggestCategory_DuplicateName_ReturnsExistingCategory() {
        UserSuggestCategoryRequest request = new UserSuggestCategoryRequest("Education", "Description");
        when(categoryRepository.findByNameIgnoreCase("Education")).thenReturn(Optional.of(testCategory));
        when(categoryMapper.toUserResponse(testCategory)).thenReturn(userCategoryResponse);

        UserCategoryResponse result = categoryService.suggestCategory(request);

        assertNotNull(result);
        assertEquals("Education", result.name());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void suggestCategory_ExistingRejectedCategory_TransitionsToPending() {
        UserSuggestCategoryRequest request = new UserSuggestCategoryRequest("Education", "Description");
        Category existingCategory = new Category();
        existingCategory.setId(1L);
        existingCategory.setName("Education");
        existingCategory.setStatus(CategoryStatus.REJECTED);

        Category savedCategory = new Category();
        savedCategory.setId(1L);
        savedCategory.setName("Education");
        savedCategory.setStatus(CategoryStatus.PENDING);

        when(categoryRepository.findByNameIgnoreCase("Education")).thenReturn(Optional.of(existingCategory));
        when(categoryRepository.save(existingCategory)).thenReturn(savedCategory);
        when(categoryMapper.toUserResponse(savedCategory)).thenReturn(new UserCategoryResponse(1L, "Education", null));

        UserCategoryResponse result = categoryService.suggestCategory(request);

        assertNotNull(result);
        assertEquals("Education", result.name());
        assertEquals(CategoryStatus.PENDING, existingCategory.getStatus());
        verify(categoryRepository, times(1)).save(existingCategory);
    }

    @Test
    void suggestCategory_ExistingHiddenCategory_TransitionsToPending() {
        UserSuggestCategoryRequest request = new UserSuggestCategoryRequest("Education", "Description");
        Category existingCategory = new Category();
        existingCategory.setId(1L);
        existingCategory.setName("Education");
        existingCategory.setStatus(CategoryStatus.HIDDEN);

        Category savedCategory = new Category();
        savedCategory.setId(1L);
        savedCategory.setName("Education");
        savedCategory.setStatus(CategoryStatus.PENDING);

        when(categoryRepository.findByNameIgnoreCase("Education")).thenReturn(Optional.of(existingCategory));
        when(categoryRepository.save(existingCategory)).thenReturn(savedCategory);
        when(categoryMapper.toUserResponse(savedCategory)).thenReturn(new UserCategoryResponse(1L, "Education", null));

        UserCategoryResponse result = categoryService.suggestCategory(request);

        assertNotNull(result);
        assertEquals("Education", result.name());
        assertEquals(CategoryStatus.PENDING, existingCategory.getStatus());
        verify(categoryRepository, times(1)).save(existingCategory);
    }

    @Test
    void createCategory_Success() {
        AdminCreateCategoryRequest request = new AdminCreateCategoryRequest("Health", "Wellness");
        Category mappedCategory = new Category();
        mappedCategory.setName("Health");
        mappedCategory.setDescription("Wellness");
        mappedCategory.setStatus(CategoryStatus.APPROVED);

        Category savedCategory = new Category();
        savedCategory.setId(3L);
        savedCategory.setName("Health");
        savedCategory.setDescription("Wellness");
        savedCategory.setStatus(CategoryStatus.APPROVED);

        AdminCategoryResponse responseDto = new AdminCategoryResponse(3L, "Health", "Wellness", CategoryStatus.APPROVED);

        when(categoryMapper.toEntity(any(AdminCreateCategoryRequest.class))).thenReturn(mappedCategory);
        when(categoryRepository.existsByNameIgnoreCase("Health")).thenReturn(false);
        when(categoryRepository.save(mappedCategory)).thenReturn(savedCategory);
        when(categoryMapper.toAdminResponse(savedCategory)).thenReturn(responseDto);

        AdminCategoryResponse result = categoryService.createCategory(request);

        assertNotNull(result);
        assertEquals("Health", result.name());
        verify(categoryRepository, times(1)).existsByNameIgnoreCase("Health");
        verify(categoryRepository, times(1)).save(mappedCategory);
    }

    @Test
    void createCategory_DuplicateName_ThrowsException() {
        AdminCreateCategoryRequest request = new AdminCreateCategoryRequest("Education", "Description");

        when(categoryRepository.existsByNameIgnoreCase("Education")).thenReturn(true);

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
                Collections.singleton(CategoryStatus.APPROVED),
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
        AdminUpdateCategoryRequest updateRequest = new AdminUpdateCategoryRequest("Renamed Education", "New Description", CategoryStatus.APPROVED);

        Category updatedCategory = new Category();
        updatedCategory.setId(1L);
        updatedCategory.setName("Renamed Education");
        updatedCategory.setDescription("New Description");
        updatedCategory.setStatus(CategoryStatus.APPROVED);

        AdminCategoryResponse updatedResponse = new AdminCategoryResponse(1L, "Renamed Education", "New Description", CategoryStatus.APPROVED);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.existsByNameIgnoreCase("Renamed Education")).thenReturn(false);
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
        AdminUpdateCategoryRequest updateRequest = new AdminUpdateCategoryRequest("Duplicate Name", "New Description", CategoryStatus.APPROVED);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.existsByNameIgnoreCase("Duplicate Name")).thenReturn(true);

        AppException exception = assertThrows(AppException.class, () ->
                categoryService.updateCategory(1L, updateRequest)
        );

        assertEquals(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS, exception.getErrorCode());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void deleteCategory_Success() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        categoryService.deleteCategory(1L);

        assertEquals(CategoryStatus.HIDDEN, testCategory.getStatus());
        verify(categoryRepository, times(1)).save(testCategory);
    }

    @Test
    void suggestCategory_Normalization_VietnameseAndSpaces_Success() {
        // String has decomposed Unicode accents (NFD form of "Tiếng Việt")
        String inputName = "  Tiê\u0301ng   Viê\u0323t  ";
        String expectedNormalizedName = "Tiếng Việt";
        String inputDescription = "  Vietnamese category  ";
        String expectedNormalizedDescription = "Vietnamese category";

        UserSuggestCategoryRequest request = new UserSuggestCategoryRequest(inputName, inputDescription);
        Category mappedCategory = new Category();
        mappedCategory.setName(expectedNormalizedName);
        mappedCategory.setDescription(expectedNormalizedDescription);
        mappedCategory.setStatus(CategoryStatus.PENDING);

        Category savedCategory = new Category();
        savedCategory.setId(3L);
        savedCategory.setName(expectedNormalizedName);
        savedCategory.setDescription(expectedNormalizedDescription);
        savedCategory.setStatus(CategoryStatus.PENDING);

        UserCategoryResponse responseDto = new UserCategoryResponse(3L, expectedNormalizedName, expectedNormalizedDescription);

        when(categoryRepository.findByNameIgnoreCase(expectedNormalizedName)).thenReturn(Optional.empty());
        when(categoryMapper.toEntity(any(UserSuggestCategoryRequest.class))).thenReturn(mappedCategory);
        when(categoryRepository.save(mappedCategory)).thenReturn(savedCategory);
        when(categoryMapper.toUserResponse(savedCategory)).thenReturn(responseDto);

        UserCategoryResponse result = categoryService.suggestCategory(request);

        assertNotNull(result);
        assertEquals(expectedNormalizedName, result.name());
        assertEquals(expectedNormalizedDescription, result.description());
        verify(categoryRepository, times(1)).findByNameIgnoreCase(expectedNormalizedName);
    }

    @Test
    void suggestCategory_BlankName_ThrowsValidationError() {
        // Name with only Unicode whitespace characters (e.g. \u2007 is figure space)
        UserSuggestCategoryRequest request = new UserSuggestCategoryRequest("  \u2007  ", "Description");

        AppException exception = assertThrows(AppException.class, () ->
                categoryService.suggestCategory(request)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals("Category name must not be blank.", exception.getMessage());
        verify(categoryRepository, never()).existsByNameIgnoreCase(anyString());
    }
}
