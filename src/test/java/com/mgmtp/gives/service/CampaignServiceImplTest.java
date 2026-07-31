package com.mgmtp.gives.service;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign.CampaignRequest;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.DonationMethod;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.mapper.CampaignMapper;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.CategoryRepository;
import com.mgmtp.gives.repository.CampaignFollowerRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.service.impl.CampaignServiceImpl;
import com.mgmtp.gives.service.NotificationService;
import com.mgmtp.gives.repository.UserPayOSConnectionRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignServiceImplTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CampaignMediaRepository campaignMediaRepository;

    @Mock
    private CampaignMapper campaignMapper;

    @Mock
    private CampaignFollowerRepository campaignFollowerRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CampaignMemberRepository campaignMemberRepository;

    @Mock
    private DonationRepository donationRepository;
 
    @Mock
    private UserPayOSConnectionRepository userPayOSConnectionRepository;

    @Mock
    private CampaignMemberService campaignMemberService;

    @InjectMocks
    private CampaignServiceImpl campaignService;

    private User testUser;
    private User testAdmin;
    private Category testCategory;
    private CampaignRequest validRequest;
    private CampaignRequest draftRequest;

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
                5000000L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(10),
                CampaignPriority.HIGH,
                CampaignStatus.PENDING,
                DonationMethod.MANUAL_QR,
                "MB Bank",
                "MB",
                "970422",
                "123456789",
                "Nguyen Van A");

        draftRequest = new CampaignRequest(
                "Kon Tum Water Project",
                "Clean water for children",
                Set.of(10L),
                true,
                true,
                5000000L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(10),
                CampaignPriority.HIGH,
                CampaignStatus.DRAFT);

        lenient().when(campaignMediaRepository.existsByCampaignIdAndDeletedAtIsNullAndIsCoverTrue(any()))
                .thenReturn(true);
    }

    @Test
    void createCampaign_Success() {
        when(categoryRepository.findAllById(anySet())).thenReturn(List.of(testCategory));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> {
            Campaign saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        Campaign result = campaignService.createCampaign(draftRequest, testUser);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("Kon Tum Water Project", result.getTitle());
        assertEquals(CampaignStatus.DRAFT, result.getStatus());
        assertEquals(testUser, result.getUser());
        assertTrue(result.getCategories().contains(testCategory));

        verify(categoryRepository, times(1)).findAllById(anySet());
        verify(campaignRepository, times(1)).save(any(Campaign.class));
    }

    @Test
    void createCampaign_Success_PendingStatus() {
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
                5000000L,
                LocalDateTime.now().plusDays(5),
                LocalDateTime.now().plusDays(2), // End date is before start date
                CampaignPriority.HIGH,
                CampaignStatus.DRAFT);

        AppException exception = assertThrows(AppException.class,
                () -> campaignService.createCampaign(invalidRequest, testUser));

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
                5000000L,
                sameTime,
                sameTime, // End date equals start date
                CampaignPriority.HIGH,
                CampaignStatus.DRAFT);

        AppException exception = assertThrows(AppException.class,
                () -> campaignService.createCampaign(invalidRequest, testUser));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verifyNoInteractions(categoryRepository, campaignRepository);
    }

    @Test
    void createCampaign_StartDateInPast_ThrowsException() {
        CampaignRequest invalidRequest = new CampaignRequest(
                "Water Project",
                "Description",
                Set.of(10L),
                true,
                true,
                5000000L,
                LocalDateTime.now().minusDays(1), // Start date in the past
                LocalDateTime.now().plusDays(5),
                CampaignPriority.HIGH,
                CampaignStatus.DRAFT);

        AppException exception = assertThrows(AppException.class,
                () -> campaignService.createCampaign(invalidRequest, testUser));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals("Start date cannot be in the past", exception.getMessage());
        verifyNoInteractions(categoryRepository, campaignRepository);
    }

    @Test
    void createCampaign_InvalidCategories_ThrowsException() {
        when(categoryRepository.findAllById(anySet())).thenReturn(Collections.emptyList());

        AppException exception = assertThrows(AppException.class,
                () -> campaignService.createCampaign(draftRequest, testUser));

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
    void getCampaignById_Success_InProgressStatus() {
        Campaign campaign = new Campaign();
        campaign.setId(100L);
        campaign.setStatus(CampaignStatus.IN_PROGRESS);

        User otherUser = new User();
        otherUser.setId(3L);
        otherUser.setRole(UserRole.USER);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));

        Campaign result = campaignService.getCampaignById(100L, otherUser);

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
    void getCampaignById_Forbidden_WhenInvisibleToUser() {
        Campaign campaign = new Campaign();
        campaign.setId(100L);
        campaign.setUser(testUser); // creator is testUser
        campaign.setStatus(CampaignStatus.PENDING); // status is PENDING

        User otherUser = new User();
        otherUser.setId(3L);
        otherUser.setRole(UserRole.USER);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));
        when(campaignFollowerRepository.existsByCampaignIdAndUserId(100L, 3L)).thenReturn(false);

        AppException exception = assertThrows(AppException.class,
                () -> campaignService.getCampaignById(100L, otherUser));

        assertEquals(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS, exception.getErrorCode());
    }

    @Test
    void getCampaignById_Success_WhenFollower() {
        Campaign campaign = new Campaign();
        campaign.setId(100L);
        campaign.setUser(testUser); // creator is testUser
        campaign.setStatus(CampaignStatus.PENDING); // status is PENDING

        User otherUser = new User();
        otherUser.setId(3L);
        otherUser.setRole(UserRole.USER);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));
        when(campaignFollowerRepository.existsByCampaignIdAndUserId(100L, 3L)).thenReturn(true);

        Campaign result = campaignService.getCampaignById(100L, otherUser);

        assertNotNull(result);
        assertEquals(100L, result.getId());
    }

    @Test
    void getCampaignById_Unauthorized_WhenCurrentUserIsNull() {
        Campaign campaign = new Campaign();
        campaign.setId(100L);
        campaign.setUser(testUser);
        campaign.setStatus(CampaignStatus.PENDING);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));

        AppException exception = assertThrows(AppException.class, () -> campaignService.getCampaignById(100L, null));

        assertEquals(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS, exception.getErrorCode());
    }

    @Test
    void getCampaignById_NotFound_WhenEntityNotExists() {
        when(campaignRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> campaignService.getCampaignById(999L, testUser));

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
    void updateCampaign_InvalidStatus_ByAdmin_ApprovedStatus_ThrowsException() {
        Campaign existing = new Campaign();
        existing.setId(100L);
        existing.setUser(testUser);
        existing.setStatus(CampaignStatus.APPROVED); // Approved status

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(campaignMemberService.canManageCampaign(100L, testAdmin)).thenReturn(true);

        AppException exception = assertThrows(AppException.class,
                () -> campaignService.updateCampaign(100L, validRequest, testAdmin));

        assertEquals(ErrorCode.INVALID_CAMPAIGN_STATUS_FOR_UPDATE, exception.getErrorCode());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    @Test
    void updateCampaign_Unauthorized_ByNonOwnerNonAdmin_ThrowsException() {
        User otherUser = new User();
        otherUser.setId(3L);
        otherUser.setRole(UserRole.USER);

        Campaign existing = new Campaign();
        existing.setId(100L);
        existing.setUser(testUser);
        existing.setStatus(CampaignStatus.IN_PROGRESS);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(campaignMemberService.canManageCampaign(100L, otherUser)).thenReturn(false);

        AppException exception = assertThrows(AppException.class,
                () -> campaignService.updateCampaign(100L, validRequest, otherUser));

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

        AppException exception = assertThrows(AppException.class,
                () -> campaignService.updateCampaign(100L, validRequest, otherUser));

        assertEquals(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS, exception.getErrorCode());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    @Test
    void updateCampaign_InvalidStatus_ByCreator_ApprovedStatus_ThrowsException() {
        Campaign existing = new Campaign();
        existing.setId(100L);
        existing.setUser(testUser);
        existing.setStatus(CampaignStatus.APPROVED); // Creator tries to edit approved campaign

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(existing));

        AppException exception = assertThrows(AppException.class,
                () -> campaignService.updateCampaign(100L, validRequest, testUser));

        assertEquals(ErrorCode.INVALID_CAMPAIGN_STATUS_FOR_UPDATE, exception.getErrorCode());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    @Test
    void deleteCampaign_Success_UserPending() {
        Campaign campaign = new Campaign();
        campaign.setId(100L);
        campaign.setUser(testUser);
        campaign.setStatus(CampaignStatus.PENDING);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));
        doNothing().when(campaignRepository).delete(campaign);

        campaignService.deleteCampaign(100L, testUser);

        verify(campaignRepository, times(1)).delete(campaign);
    }

    @Test
    void deleteCampaign_Success_UserRejected() {
        Campaign campaign = new Campaign();
        campaign.setId(100L);
        campaign.setUser(testUser);
        campaign.setStatus(CampaignStatus.REJECTED);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));
        doNothing().when(campaignRepository).delete(campaign);

        campaignService.deleteCampaign(100L, testUser);

        verify(campaignRepository, times(1)).delete(campaign);
    }

    @Test
    void deleteCampaign_Success_System() {
        Campaign campaign = new Campaign();
        campaign.setId(100L);
        campaign.setUser(testUser);
        campaign.setStatus(CampaignStatus.REJECTED);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));
        doNothing().when(campaignRepository).delete(campaign);

        campaignService.deleteCampaign(100L, null);

        verify(campaignRepository, times(1)).delete(campaign);
    }

    @Test
    void deleteCampaign_Fail_NotOwner() {
        User anotherUser = new User();
        anotherUser.setId(99L);
        anotherUser.setEmail("other@example.com");

        Campaign campaign = new Campaign();
        campaign.setId(100L);
        campaign.setUser(anotherUser);
        campaign.setStatus(CampaignStatus.PENDING);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));

        AppException exception = assertThrows(AppException.class, () -> campaignService.deleteCampaign(100L, testUser));

        assertEquals(ErrorCode.UNAUTHORIZED_CAMPAIGN_DELETE, exception.getErrorCode());
        verify(campaignRepository, never()).delete(any(Campaign.class));
    }

    @Test
    void deleteCampaign_Fail_InvalidStatus() {
        Campaign campaign = new Campaign();
        campaign.setId(100L);
        campaign.setUser(testUser);
        campaign.setStatus(CampaignStatus.APPROVED);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));

        AppException exception = assertThrows(AppException.class, () -> campaignService.deleteCampaign(100L, testUser));

        assertEquals(ErrorCode.INVALID_CAMPAIGN_STATUS_FOR_DELETE, exception.getErrorCode());
        verify(campaignRepository, never()).delete(any(Campaign.class));
    }

    @Test
    void createCampaign_DeletedCategory_ThrowsException() {
        Category deletedCategory = new Category();
        deletedCategory.setId(10L);
        deletedCategory.setName("Deleted Category");
        deletedCategory.setDeletedAt(LocalDateTime.now());

        when(categoryRepository.findAllById(anySet())).thenReturn(List.of(deletedCategory));

        AppException exception = assertThrows(AppException.class,
                () -> campaignService.createCampaign(draftRequest, testUser));

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
    void updateCampaign_ResetStatusToPending_WhenRejectedAndUpdatedByAdmin() {
        Campaign existing = new Campaign();
        existing.setId(100L);
        existing.setUser(testUser);
        existing.setStatus(CampaignStatus.REJECTED);

        when(campaignRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(campaignMemberService.canManageCampaign(100L, testAdmin)).thenReturn(true);
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
                null, // null status preserves existing status
                validRequest.donationMethod(),
                validRequest.bankName(),
                validRequest.bankCode(),
                validRequest.bankBin(),
                validRequest.bankAccountNumber(),
                validRequest.bankAccountHolderName()
        );

        Campaign result = campaignService.updateCampaign(100L, adminRequest, testAdmin);

        assertNotNull(result);
        assertEquals(CampaignStatus.PENDING, result.getStatus());
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
                List.of(10L),
                1L,
                "keyword",
                null,
                testUser,
                PageRequest.of(0, 10));

        assertNotNull(result);
        verify(campaignRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAllCampaigns_isFollowing_True_Success() {
        Page<Campaign> page = new PageImpl<>(Collections.emptyList());
        when(campaignRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<Campaign> result = campaignService.getAllCampaigns(
                CampaignStatus.APPROVED,
                CampaignPriority.HIGH,
                List.of(10L),
                1L,
                "keyword",
                true,
                testUser,
                PageRequest.of(0, 10));

        assertNotNull(result);
        verify(campaignRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void startApprovedCampaignsScheduled_Success() {
        Campaign campaign = new Campaign();
        campaign.setId(1L);
        campaign.setTitle("Starting Campaign");
        campaign.setStatus(CampaignStatus.APPROVED);

        when(campaignRepository.findByStatusAndStartDateBetween(
                eq(CampaignStatus.APPROVED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(campaign));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

        campaignService.startApprovedCampaignsScheduled();

        assertEquals(CampaignStatus.IN_PROGRESS, campaign.getStatus());
        verify(campaignRepository, times(1)).save(campaign);
    }
    @Test
    void isFollowed_ShouldReturnTrue_WhenRecordExists() {
        when(campaignFollowerRepository.existsByCampaignIdAndUserId(1L, 2L)).thenReturn(true);
        boolean result = campaignService.isFollowed(1L, 2L);
        assertTrue(result);
    }

    @Test
    void isFollowed_ShouldReturnFalse_WhenRecordDoesNotExist() {
        when(campaignFollowerRepository.existsByCampaignIdAndUserId(1L, 2L)).thenReturn(false);
        boolean result = campaignService.isFollowed(1L, 2L);
        assertFalse(result);
    }

    @Test
    void isJoined_ShouldReturnTrue_WhenRecordExists() {
        when(campaignMemberRepository.existsByCampaignIdAndUserIdAndRoleInCampaign(1L, 2L,
                CampaignMemberRole.VOLUNTEER)).thenReturn(true);
        boolean result = campaignService.isJoined(1L, 2L);
        assertTrue(result);
    }

    @Test
    void isJoined_ShouldReturnFalse_WhenRecordDoesNotExist() {
        when(campaignMemberRepository.existsByCampaignIdAndUserIdAndRoleInCampaign(1L, 2L,
                CampaignMemberRole.VOLUNTEER)).thenReturn(false);
        boolean result = campaignService.isJoined(1L, 2L);
        assertFalse(result);
    }

    @Test
    void getVolunteersCount_ShouldReturnCorrectCount() {
        when(campaignMemberRepository.countByCampaignIdAndRoleInCampaign(1L, CampaignMemberRole.VOLUNTEER))
                .thenReturn(5L);
        long result = campaignService.getVolunteersCount(1L);
        assertEquals(5L, result);
    }

    @Test
    void getDonorsCount_ShouldReturnCorrectCount() {
        when(donationRepository.countDistinctDonorsByCampaignIdAndStatusSuccessful(1L)).thenReturn(10L);
        long result = campaignService.getDonorsCount(1L);
        assertEquals(10L, result);
    }

    @Test
    void createCampaign_ManualQR_MissingBankDetails_ThrowsException() {
        CampaignRequest request = new CampaignRequest(
                "Water Project",
                "Description",
                Set.of(10L),
                true,
                true,
                5000000L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(10),
                CampaignPriority.HIGH,
                CampaignStatus.PENDING,
                com.mgmtp.gives.enums.DonationMethod.MANUAL_QR,
                null, // missing bankName
                null, // bankCode
                null, // bankBin
                "123456789",
                "NGUYEN VAN A"
        );

        assertThrows(AppException.class, () -> campaignService.createCampaign(request, testUser));
    }

    @Test
    void createCampaign_ManualQR_WithBankDetails_Success() {
        CampaignRequest request = new CampaignRequest(
                "Water Project",
                "Description",
                Set.of(10L),
                true,
                true,
                5000000L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(10),
                CampaignPriority.HIGH,
                CampaignStatus.PENDING,
                com.mgmtp.gives.enums.DonationMethod.MANUAL_QR,
                "MBBank",
                "MB",
                "970422",
                "123456789",
                "NGUYEN VAN A"
        );

        when(categoryRepository.findAllById(anySet())).thenReturn(List.of(testCategory));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> {
            Campaign saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        Campaign result = campaignService.createCampaign(request, testUser);

        assertNotNull(result);
        assertEquals(com.mgmtp.gives.enums.DonationMethod.MANUAL_QR, result.getDonationMethod());
        assertEquals("MB", result.getBankCode());
        assertEquals("970422", result.getBankBin());
        assertEquals("123456789", result.getBankAccountNumber());
        assertEquals("NGUYEN VAN A", result.getBankAccountHolderName());
    }
}
