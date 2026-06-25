package com.mgmtp.gives.service;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign.CampaignRequest;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.CategoryRepository;
import com.mgmtp.gives.service.impl.CampaignServiceImpl;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignServiceImplTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CampaignServiceImpl campaignService;

    private User testUser;
    private User testAdmin;
    private Category testCategory;
    private CampaignRequest validRequest;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("user@example.com");
        testUser.setRole(UserRole.USER);

        testAdmin = new User();
        testAdmin.setId(2L);
        testAdmin.setEmail("admin@example.com");
        testAdmin.setRole(UserRole.ADMIN);

        testCategory = new Category();
        testCategory.setId(10L);
        testCategory.setName("Education");

        validRequest = new CampaignRequest(
                "Kon Tum Water Project",
                "Clean water for children",
                Set.of(10L),
                true,
                true,
                5000L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(10),
                CampaignPriority.HIGH,
                CampaignStatus.PENDING
        );
    }

    @Test
    void createCampaign_Success() {
        when(categoryRepository.findAllById(anySet())).thenReturn(List.of(testCategory));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> {
            Campaign saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        Campaign result = campaignService.createCampaign(validRequest, testUser);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("Kon Tum Water Project", result.getTitle());
        assertEquals(CampaignStatus.PENDING, result.getStatus());
        assertEquals(testUser, result.getUser());
        assertTrue(result.getCategories().contains(testCategory));

        verify(categoryRepository, times(1)).findAllById(anySet());
        verify(campaignRepository, times(1)).save(any(Campaign.class));
    }

    @Test
    void createCampaign_InvalidDateRange_ThrowsException() {
        CampaignRequest invalidRequest = new CampaignRequest(
                "Invalid Dates",
                "Description",
                Set.of(10L),
                true,
                true,
                5000L,
                LocalDateTime.now().plusDays(5),
                LocalDateTime.now().plusDays(2), // End date is before start date
                CampaignPriority.HIGH,
                CampaignStatus.PENDING
        );

        AppException exception = assertThrows(AppException.class, () -> 
                campaignService.createCampaign(invalidRequest, testUser)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals("Start date must be before end date", exception.getMessage());
        verifyNoInteractions(categoryRepository, campaignRepository);
    }

    @Test
    void createCampaign_EqualDates_ThrowsException() {
        LocalDateTime sameTime = LocalDateTime.now().plusDays(1);
        CampaignRequest invalidRequest = new CampaignRequest(
                "Invalid Dates",
                "Description",
                Set.of(10L),
                true,
                true,
                5000L,
                sameTime,
                sameTime, // End date equals start date
                CampaignPriority.HIGH,
                CampaignStatus.PENDING
        );

        AppException exception = assertThrows(AppException.class, () -> 
                campaignService.createCampaign(invalidRequest, testUser)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verifyNoInteractions(categoryRepository, campaignRepository);
    }

    @Test
    void createCampaign_InvalidCategories_ThrowsException() {
        when(categoryRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        AppException exception = assertThrows(AppException.class, () -> 
                campaignService.createCampaign(validRequest, testUser)
        );

        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, exception.getErrorCode());
        verify(categoryRepository, times(1)).findAllById(anySet());
        verifyNoInteractions(campaignRepository);
    }

    @Test
    void getCampaignById_Success_ByAdmin() {
        Campaign campaign = new Campaign();
        campaign.setId(100L);
        campaign.setStatus(CampaignStatus.PENDING);
        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));

        Campaign result = campaignService.getCampaignById(100L, testAdmin);

        assertNotNull(result);
        assertEquals(100L, result.getId());
    }

    @Test
    void getCampaignById_Success_ByCreator() {
        Campaign campaign = new Campaign();
        campaign.setId(100L);
        campaign.setUser(testUser);
        campaign.setStatus(CampaignStatus.PENDING);
        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));

        Campaign result = campaignService.getCampaignById(100L, testUser);

        assertNotNull(result);
        assertEquals(100L, result.getId());
    }

    @Test
    void getCampaignById_Success_ApprovedStatus() {
        Campaign campaign = new Campaign();
        campaign.setId(100L);
        campaign.setStatus(CampaignStatus.APPROVED);

        User otherUser = new User();
        otherUser.setId(3L);
        otherUser.setRole(UserRole.USER);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));

        Campaign result = campaignService.getCampaignById(100L, otherUser);

        assertNotNull(result);
        assertEquals(100L, result.getId());
    }

    @Test
    void getCampaignById_NotFound_WhenInvisibleToUser() {
        Campaign campaign = new Campaign();
        campaign.setId(100L);
        campaign.setUser(testUser); // creator is testUser
        campaign.setStatus(CampaignStatus.PENDING); // status is PENDING

        User otherUser = new User();
        otherUser.setId(3L);
        otherUser.setRole(UserRole.USER);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> 
                campaignService.getCampaignById(100L, otherUser)
        );

        assertEquals(ErrorCode.CAMPAIGN_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getCampaignById_NotFound_WhenEntityNotExists() {
        when(campaignRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> 
                campaignService.getCampaignById(999L, testUser)
        );

        assertEquals(ErrorCode.CAMPAIGN_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updateCampaign_Success_ByCreator_PendingStatus() {
        Campaign existing = new Campaign();
        existing.setId(100L);
        existing.setUser(testUser);
        existing.setStatus(CampaignStatus.DRAFT);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findAllById(anySet())).thenReturn(List.of(testCategory));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Campaign result = campaignService.updateCampaign(100L, validRequest, testUser);

        assertNotNull(result);
        assertEquals("Kon Tum Water Project", result.getTitle());
        verify(campaignRepository, times(1)).save(any(Campaign.class));
    }

    @Test
    void updateCampaign_Success_ByAdmin_ApprovedStatus() {
        Campaign existing = new Campaign();
        existing.setId(100L);
        existing.setUser(testUser);
        existing.setStatus(CampaignStatus.APPROVED); // Approved status

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findAllById(anySet())).thenReturn(List.of(testCategory));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Admin updates an approved campaign
        Campaign result = campaignService.updateCampaign(100L, validRequest, testAdmin);

        assertNotNull(result);
        assertEquals("Kon Tum Water Project", result.getTitle());
        verify(campaignRepository, times(1)).save(any(Campaign.class));
    }

    @Test
    void updateCampaign_Unauthorized_ByNonOwnerNonAdmin_ThrowsException() {
        User otherUser = new User();
        otherUser.setId(3L);
        otherUser.setRole(UserRole.USER);

        Campaign existing = new Campaign();
        existing.setId(100L);
        existing.setUser(testUser);
        existing.setStatus(CampaignStatus.APPROVED);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(existing));

        AppException exception = assertThrows(AppException.class, () -> 
                campaignService.updateCampaign(100L, validRequest, otherUser)
        );

        assertEquals(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE, exception.getErrorCode());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    @Test
    void updateCampaign_NotFound_ByNonOwnerNonAdmin_WhenPending() {
        User otherUser = new User();
        otherUser.setId(3L);
        otherUser.setRole(UserRole.USER);

        Campaign existing = new Campaign();
        existing.setId(100L);
        existing.setUser(testUser);
        existing.setStatus(CampaignStatus.PENDING);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(existing));

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> 
                campaignService.updateCampaign(100L, validRequest, otherUser)
        );

        assertEquals(ErrorCode.CAMPAIGN_NOT_FOUND, exception.getErrorCode());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    @Test
    void updateCampaign_InvalidStatus_ByCreator_ApprovedStatus_ThrowsException() {
        Campaign existing = new Campaign();
        existing.setId(100L);
        existing.setUser(testUser);
        existing.setStatus(CampaignStatus.APPROVED); // Creator tries to edit approved campaign

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(existing));

        AppException exception = assertThrows(AppException.class, () -> 
                campaignService.updateCampaign(100L, validRequest, testUser)
        );

        assertEquals(ErrorCode.INVALID_CAMPAIGN_STATUS_FOR_UPDATE, exception.getErrorCode());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    @Test
    void deleteCampaign_Success() {
        Campaign campaign = new Campaign();
        campaign.setId(100L);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));
        doNothing().when(campaignRepository).delete(campaign);

        campaignService.deleteCampaign(100L, testAdmin);

        verify(campaignRepository, times(1)).delete(campaign);
    }

    @Test
    void createCampaign_RejectedCategory_ThrowsException() {
        Category rejectedCategory = new Category();
        rejectedCategory.setId(10L);
        rejectedCategory.setName("Rejected Category");
        rejectedCategory.setStatus(com.mgmtp.gives.enums.CategoryStatus.REJECTED);

        when(categoryRepository.findAllById(anySet())).thenReturn(List.of(rejectedCategory));

        AppException exception = assertThrows(AppException.class, () ->
                campaignService.createCampaign(validRequest, testUser)
        );

        assertEquals(ErrorCode.CATEGORY_NOT_AVAILABLE, exception.getErrorCode());
        verify(categoryRepository, times(1)).findAllById(anySet());
        verifyNoInteractions(campaignRepository);
    }

    @Test
    void createCampaign_HiddenCategory_ThrowsException() {
        Category hiddenCategory = new Category();
        hiddenCategory.setId(10L);
        hiddenCategory.setName("Hidden Category");
        hiddenCategory.setStatus(com.mgmtp.gives.enums.CategoryStatus.HIDDEN);

        when(categoryRepository.findAllById(anySet())).thenReturn(List.of(hiddenCategory));

        AppException exception = assertThrows(AppException.class, () ->
                campaignService.createCampaign(validRequest, testUser)
        );

        assertEquals(ErrorCode.CATEGORY_NOT_AVAILABLE, exception.getErrorCode());
        verify(categoryRepository, times(1)).findAllById(anySet());
        verifyNoInteractions(campaignRepository);
    }

    @Test
    void updateCampaign_ResetStatusToPending_WhenRejectedAndUpdatedByCreator() {
        Campaign existing = new Campaign();
        existing.setId(100L);
        existing.setUser(testUser);
        existing.setStatus(CampaignStatus.REJECTED);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findAllById(anySet())).thenReturn(List.of(testCategory));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Campaign result = campaignService.updateCampaign(100L, validRequest, testUser);

        assertNotNull(result);
        assertEquals("Kon Tum Water Project", result.getTitle());
        assertEquals(CampaignStatus.PENDING, result.getStatus());
        verify(campaignRepository, times(1)).save(any(Campaign.class));
    }

    @Test
    void updateCampaign_KeepRejectedStatus_WhenRejectedAndUpdatedByAdmin() {
        Campaign existing = new Campaign();
        existing.setId(100L);
        existing.setUser(testUser);
        existing.setStatus(CampaignStatus.REJECTED);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findAllById(anySet())).thenReturn(List.of(testCategory));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CampaignRequest adminRequest = new CampaignRequest(
                validRequest.title(),
                validRequest.description(),
                validRequest.categories(),
                validRequest.acceptsMoney(),
                validRequest.acceptsGoods(),
                validRequest.target(),
                validRequest.startDate(),
                validRequest.endDate(),
                validRequest.priority(),
                null // null status preserves existing status
        );

        Campaign result = campaignService.updateCampaign(100L, adminRequest, testAdmin);

        assertNotNull(result);
        assertEquals(CampaignStatus.REJECTED, result.getStatus());
        verify(campaignRepository, times(1)).save(any(Campaign.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAllCampaigns_Success() {
        Page<Campaign> page = new PageImpl<>(Collections.emptyList());
        when(campaignRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<Campaign> result = campaignService.getAllCampaigns(
                CampaignStatus.APPROVED,
                CampaignPriority.HIGH,
                10L,
                1L,
                "keyword",
                testUser,
                PageRequest.of(0, 10)
        );

        assertNotNull(result);
        verify(campaignRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void createCampaign_ApprovedStatus_ByUser_ThrowsException() {
        CampaignRequest approvedRequest = new CampaignRequest(
                validRequest.title(),
                validRequest.description(),
                validRequest.categories(),
                validRequest.acceptsMoney(),
                validRequest.acceptsGoods(),
                validRequest.target(),
                validRequest.startDate(),
                validRequest.endDate(),
                validRequest.priority(),
                CampaignStatus.APPROVED // illegal APPROVED status
        );

        AppException exception = assertThrows(AppException.class, () ->
                campaignService.createCampaign(approvedRequest, testUser)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    @Test
    void updateCampaign_ApprovedStatus_ByUser_ThrowsException() {
        Campaign existing = new Campaign();
        existing.setId(100L);
        existing.setUser(testUser);
        existing.setStatus(CampaignStatus.DRAFT);

        CampaignRequest approvedRequest = new CampaignRequest(
                validRequest.title(),
                validRequest.description(),
                validRequest.categories(),
                validRequest.acceptsMoney(),
                validRequest.acceptsGoods(),
                validRequest.target(),
                validRequest.startDate(),
                validRequest.endDate(),
                validRequest.priority(),
                CampaignStatus.APPROVED // illegal APPROVED status
        );

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(existing));

        AppException exception = assertThrows(AppException.class, () ->
                campaignService.updateCampaign(100L, approvedRequest, testUser)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    @Test
    void updateCampaign_NoStatus_TransitionsRejectedStatusToPending_ForUser() {
        Campaign campaign = new Campaign();
        campaign.setId(100L);
        campaign.setUser(testUser);
        campaign.setStatus(CampaignStatus.REJECTED);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));
        when(categoryRepository.findAllById(anySet())).thenReturn(List.of(testCategory));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CampaignRequest userRequest = new CampaignRequest(
                validRequest.title(),
                validRequest.description(),
                validRequest.categories(),
                validRequest.acceptsMoney(),
                validRequest.acceptsGoods(),
                validRequest.target(),
                validRequest.startDate(),
                validRequest.endDate(),
                validRequest.priority(),
                null // null status will auto-transition REJECTED to PENDING for regular user
        );

        Campaign result = campaignService.updateCampaign(100L, userRequest, testUser);

        assertNotNull(result);
        assertEquals(CampaignStatus.PENDING, result.getStatus());
        verify(campaignRepository, times(1)).save(any(Campaign.class));
    }

    @Test
    void createCampaign_PendingStatus_MissingTarget_ThrowsException() {
        CampaignRequest invalidRequest = new CampaignRequest(
                validRequest.title(),
                validRequest.description(),
                validRequest.categories(),
                validRequest.acceptsMoney(),
                validRequest.acceptsGoods(),
                null, // target is null
                validRequest.startDate(),
                validRequest.endDate(),
                validRequest.priority(),
                CampaignStatus.PENDING
        );

        AppException exception = assertThrows(AppException.class, () ->
                campaignService.createCampaign(invalidRequest, testUser)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Target amount is required"));
    }

    @Test
    void createCampaign_PendingStatus_MissingStartDate_ThrowsException() {
        CampaignRequest invalidRequest = new CampaignRequest(
                validRequest.title(),
                validRequest.description(),
                validRequest.categories(),
                validRequest.acceptsMoney(),
                validRequest.acceptsGoods(),
                validRequest.target(),
                null, // start date is null
                validRequest.endDate(),
                validRequest.priority(),
                CampaignStatus.PENDING
        );

        AppException exception = assertThrows(AppException.class, () ->
                campaignService.createCampaign(invalidRequest, testUser)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Start date is required"));
    }

    @Test
    void createCampaign_PendingStatus_MissingEndDate_ThrowsException() {
        CampaignRequest invalidRequest = new CampaignRequest(
                validRequest.title(),
                validRequest.description(),
                validRequest.categories(),
                validRequest.acceptsMoney(),
                validRequest.acceptsGoods(),
                validRequest.target(),
                validRequest.startDate(),
                null, // end date is null
                validRequest.priority(),
                CampaignStatus.PENDING
        );

        AppException exception = assertThrows(AppException.class, () ->
                campaignService.createCampaign(invalidRequest, testUser)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("End date is required"));
    }

    @Test
    void createCampaign_PendingStatus_NoTarget_AcceptsGoodsOnly_Success() {
        CampaignRequest goodsOnlyRequest = new CampaignRequest(
                validRequest.title(),
                validRequest.description(),
                validRequest.categories(),
                false, // acceptsMoney = false
                true,  // acceptsGoods = true
                null,  // target is null
                validRequest.startDate(),
                validRequest.endDate(),
                validRequest.priority(),
                CampaignStatus.PENDING
        );

        when(categoryRepository.findAllById(anySet())).thenReturn(List.of(testCategory));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> {
            Campaign saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        Campaign result = campaignService.createCampaign(goodsOnlyRequest, testUser);

        assertNotNull(result);
        assertFalse(result.isAcceptsMoney());
        assertTrue(result.isAcceptsGoods());
        assertNull(result.getTarget());
    }

    @Test
    void updateCampaign_PreservesAcceptsFieldsWhenNullInRequest() {
        Campaign existing = new Campaign();
        existing.setId(100L);
        existing.setUser(testUser);
        existing.setStatus(CampaignStatus.DRAFT);
        existing.setAcceptsMoney(false);
        existing.setAcceptsGoods(false);

        CampaignRequest partialRequest = new CampaignRequest(
                validRequest.title(),
                validRequest.description(),
                validRequest.categories(),
                null, // acceptsMoney is null
                null, // acceptsGoods is null
                validRequest.target(),
                validRequest.startDate(),
                validRequest.endDate(),
                validRequest.priority(),
                CampaignStatus.DRAFT
        );

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findAllById(anySet())).thenReturn(List.of(testCategory));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Campaign result = campaignService.updateCampaign(100L, partialRequest, testUser);

        assertNotNull(result);
        assertFalse(result.isAcceptsMoney()); // Should remain false (preserved)
        assertFalse(result.isAcceptsGoods()); // Should remain false (preserved)
    }
}
