package com.mgmtp.gives.service;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.donation.PayOSRequest;
import com.mgmtp.gives.dto.donation.PayOSResponse;
import com.mgmtp.gives.dto.donation.DonationResponse;
import com.mgmtp.gives.dto.donation.EditDonationRequest;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.DonationMethod;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.impl.DonationServiceImpl;
import com.mgmtp.gives.notification.publisher.DonationNotificationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DonationServiceImplTest {

    @Mock
    private DonationRepository donationRepository;

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private CampaignFollowerService campaignFollowerService;

    @Mock
    private DonationNotificationPublisher publisher;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PayOS payOS;

    @Mock
    private PayOSClientProvider payOSClientProvider;

    @Mock
    private org.springframework.context.ApplicationContext applicationContext;

    @Mock
    private CampaignMemberService campaignMemberService;

    @InjectMocks
    private DonationServiceImpl donationService;

    private User testUser;
    private Campaign testCampaign;
    private Donation testDonation;

    @BeforeEach
    void setUp() {
        lenient().when(applicationContext.getBean(DonationService.class)).thenReturn(donationService);
        lenient().when(payOSClientProvider.getClientForCampaign(any())).thenReturn(payOS);
        ReflectionTestUtils.setField(donationService, "payOSCancelUrl", "http://localhost:5173/cancel");
        ReflectionTestUtils.setField(donationService, "payOSReturnUrl", "http://localhost:5173/return");

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("user@example.com");
        testUser.setFullName("Test User");
        testUser.setRole(UserRole.USER);

        testCampaign = new Campaign();
        testCampaign.setId(1L);
        testCampaign.setTitle("Test Campaign");
        testCampaign.setStatus(CampaignStatus.IN_PROGRESS);
        testCampaign.setDonationMethod(DonationMethod.PAYOS);

        testDonation = new Donation();
        testDonation.setId(10L);
        testDonation.setUser(testUser);
        testDonation.setCampaign(testCampaign);
        testDonation.setType(DonationType.MONEY);
        testDonation.setAmount(100000L);
        testDonation.setStatus(DonationStatus.PENDING);
    }

    private void setupMockSecurityContext(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setRole(UserRole.USER);
        user.setEmail("user@example.com");

        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUser()).thenReturn(user);

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void createPayOSDonation_Success() {
        PayOSRequest request = new PayOSRequest(1L, 100000L, false, "Hello test");
        
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(testCampaign));
        
        Donation savedPending = new Donation();
        savedPending.setId(10L);
        savedPending.setUser(testUser);
        savedPending.setCampaign(testCampaign);
        savedPending.setType(DonationType.MONEY);
        savedPending.setAmount(100000L);
        savedPending.setStatus(DonationStatus.PENDING);
        savedPending.setMessage("Hello test");
        
        when(donationRepository.save(any(Donation.class))).thenReturn(savedPending);

        PaymentRequestsService paymentRequestsService = mock(PaymentRequestsService.class);
        CreatePaymentLinkResponse paymentResponse = mock(CreatePaymentLinkResponse.class);
        when(paymentResponse.getPaymentLinkId()).thenReturn("link-123");
        when(paymentResponse.getCheckoutUrl()).thenReturn("https://checkout.url");
        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(paymentResponse);
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);

        PayOSResponse result = donationService.createPayOSDonation(request, testUser);

        assertNotNull(result);
        assertEquals(10L, result.donationId());
        assertEquals("https://checkout.url", result.checkoutUrl());
        assertEquals(100000L, result.amount());
        
        verify(donationRepository, times(2)).save(any(Donation.class));
    }

    @Test
    void createPayOSDonation_CampaignNotInProgress() {
        testCampaign.setStatus(CampaignStatus.COMPLETED);
        PayOSRequest request = new PayOSRequest(1L, 100000L, false, "Hello test");
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(testCampaign));

        AppException exception = assertThrows(AppException.class, () ->
                donationService.createPayOSDonation(request, testUser));

        assertEquals(ErrorCode.CAMPAIGN_NOT_IN_PROGRESS, exception.getErrorCode());
        verify(donationRepository, never()).save(any());
    }

    @Test
    void createPayOSDonation_PayOSException_SetsStatusToFailed() {
        PayOSRequest request = new PayOSRequest(1L, 100000L, false, "Hello test");
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(testCampaign));
        
        Donation savedPending = new Donation();
        savedPending.setId(10L);
        savedPending.setUser(testUser);
        savedPending.setCampaign(testCampaign);
        savedPending.setType(DonationType.MONEY);
        savedPending.setAmount(100000L);
        savedPending.setStatus(DonationStatus.PENDING);
        
        when(donationRepository.save(any(Donation.class))).thenReturn(savedPending);

        PaymentRequestsService paymentRequestsService = mock(PaymentRequestsService.class);
        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenThrow(new RuntimeException("PayOS down"));
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);

        AppException exception = assertThrows(AppException.class, () ->
                donationService.createPayOSDonation(request, testUser));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(donationRepository, times(2)).save(any(Donation.class)); // once for pending, once for failed
    }

    @Test
    void confirmPayOSDonationByPaymentLinkId_Success() {
        when(donationRepository.findByTransactionId("link-123")).thenReturn(Optional.of(testDonation));
        when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DonationResponse response = donationService.confirmPayOSDonationByPaymentLinkId("link-123");

        assertNotNull(response);
        verify(campaignFollowerService).autoFollow(1L, 1L);
        verify(publisher).publishDonationConfirmedEvents(any(Donation.class));
        verify(notificationService).broadcastDashboardUpdate();
    }

    @Test
    void confirmPayOSDonationByPaymentLinkId_AlreadyConfirmed() {
        testDonation.setStatus(DonationStatus.SUCCESSFUL);
        when(donationRepository.findByTransactionId("link-123")).thenReturn(Optional.of(testDonation));

        DonationResponse response = donationService.confirmPayOSDonationByPaymentLinkId("link-123");

        assertNotNull(response);
        verify(donationRepository, never()).save(any());
        verify(campaignFollowerService, never()).autoFollow(anyLong(), anyLong());
    }

    @Test
    void cancelPayOSDonation_Success() {
        setupMockSecurityContext(1L);
        when(donationRepository.findById(10L)).thenReturn(Optional.of(testDonation));
        when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DonationResponse response = donationService.cancelPayOSDonation(10L);

        assertNotNull(response);
        assertEquals(DonationStatus.CANCELLED, testDonation.getStatus());
        verify(notificationService).broadcastDashboardUpdate();
    }

    @Test
    void cancelPayOSDonation_Unauthorized_Bola() {
        setupMockSecurityContext(99L); // Mock different user
        when(donationRepository.findById(10L)).thenReturn(Optional.of(testDonation));

        AppException exception = assertThrows(AppException.class, () ->
                donationService.cancelPayOSDonation(10L));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(donationRepository, never()).save(any());
    }

    @Test
    void verifyPayOSUserTransaction_Success() {
        setupMockSecurityContext(1L);
        testDonation.setTransactionId("link-123");
        when(donationRepository.findById(10L)).thenReturn(Optional.of(testDonation));
        when(donationRepository.findByTransactionId("link-123")).thenReturn(Optional.of(testDonation));
        when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentRequestsService paymentRequestsService = mock(PaymentRequestsService.class);
        PaymentLink paymentLink = mock(PaymentLink.class);
        when(paymentLink.getStatus()).thenReturn(PaymentLinkStatus.PAID);
        when(paymentRequestsService.get("link-123")).thenReturn(paymentLink);
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);

        DonationResponse response = donationService.verifyPayOSUserTransaction(10L);

        assertNotNull(response);
        verify(campaignFollowerService).autoFollow(1L, 1L);
    }

    @Test
    void verifyPayOSUserTransaction_Pending() {
        setupMockSecurityContext(1L);
        testDonation.setTransactionId("link-123");
        when(donationRepository.findById(10L)).thenReturn(Optional.of(testDonation));

        PaymentRequestsService paymentRequestsService = mock(PaymentRequestsService.class);
        PaymentLink paymentLink = mock(PaymentLink.class);
        when(paymentLink.getStatus()).thenReturn(PaymentLinkStatus.PENDING);
        when(paymentRequestsService.get("link-123")).thenReturn(paymentLink);
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);

        AppException exception = assertThrows(AppException.class, () ->
                donationService.verifyPayOSUserTransaction(10L));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    @Test
    void verifyPayOSUserTransaction_AlreadySuccessful() {
        setupMockSecurityContext(1L);
        testDonation.setStatus(DonationStatus.SUCCESSFUL);
        when(donationRepository.findById(10L)).thenReturn(Optional.of(testDonation));
        DonationResponse response = donationService.verifyPayOSUserTransaction(10L);

        assertNotNull(response);
        verifyNoInteractions(payOS);
    }

    @Test
    void confirmCampaignDonation_Success() {
        when(donationRepository.findById(10L)).thenReturn(Optional.of(testDonation));
        when(campaignMemberService.canManageCampaign(1L, testUser)).thenReturn(true);
        when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DonationResponse response = donationService.confirmCampaignDonation(10L, testUser);

        assertNotNull(response);
        assertEquals(DonationStatus.SUCCESSFUL, testDonation.getStatus());
        assertEquals(testUser, testDonation.getConfirmedBy());
        assertNotNull(testDonation.getConfirmedAt());
        verify(publisher).publishDonationConfirmedEvents(any(Donation.class));
        verify(notificationService).broadcastDashboardUpdate();
    }

    @Test
    void confirmCampaignDonation_Unauthorized() {
        when(donationRepository.findById(10L)).thenReturn(Optional.of(testDonation));
        when(campaignMemberService.canManageCampaign(1L, testUser)).thenReturn(false);

        AppException exception = assertThrows(AppException.class, () ->
                donationService.confirmCampaignDonation(10L, testUser));

        assertEquals(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE, exception.getErrorCode());
    }

    @Test
    void rejectCampaignDonation_Success() {
        testDonation.setAmount(100000L);
        when(donationRepository.findById(10L)).thenReturn(Optional.of(testDonation));
        when(campaignMemberService.canManageCampaign(1L, testUser)).thenReturn(true);
        when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DonationResponse response = donationService.rejectCampaignDonation(10L, "Invalid signature", testUser);

        assertNotNull(response);
        assertEquals(DonationStatus.REJECTED, testDonation.getStatus());
        assertEquals("Invalid signature", testDonation.getRejectReason());
        verify(notificationService).broadcastDashboardUpdate();
    }

    @Test
    void rejectCampaignDonation_Unauthorized() {
        when(donationRepository.findById(10L)).thenReturn(Optional.of(testDonation));
        when(campaignMemberService.canManageCampaign(1L, testUser)).thenReturn(false);

        AppException exception = assertThrows(AppException.class, () ->
                donationService.rejectCampaignDonation(10L, "Reason", testUser));

        assertEquals(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE, exception.getErrorCode());
    }

    @Test
    void editCampaignDonation_PendingToRejected() {
        testDonation.setStatus(DonationStatus.PENDING);
        testDonation.setAmount(100000L);
        EditDonationRequest request = new EditDonationRequest("Incorrect transaction code");

        when(donationRepository.findById(10L)).thenReturn(Optional.of(testDonation));
        when(campaignMemberService.canManageCampaign(1L, testUser)).thenReturn(true);
        when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DonationResponse response = donationService.editCampaignDonation(10L, request, testUser);

        assertNotNull(response);
        assertEquals(DonationStatus.REJECTED, testDonation.getStatus());
        assertEquals("Incorrect transaction code", testDonation.getRejectReason());
        verify(notificationService).broadcastDashboardUpdate();
    }

    @Test
    void editCampaignDonation_FailedToSuccessful_ClearsRejectReason() {
        testDonation.setStatus(DonationStatus.CANCELLED);
        testDonation.setRejectReason("Previous failure reason");
        EditDonationRequest request = new EditDonationRequest("Bank transfer receipt verified");

        when(donationRepository.findById(10L)).thenReturn(Optional.of(testDonation));
        when(campaignMemberService.canManageCampaign(1L, testUser)).thenReturn(true);
        when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DonationResponse response = donationService.editCampaignDonation(10L, request, testUser);

        assertNotNull(response);
        assertEquals(DonationStatus.SUCCESSFUL, testDonation.getStatus());
        assertNull(testDonation.getRejectReason());
        assertEquals(testUser, testDonation.getConfirmedBy());
        verify(notificationService).broadcastDashboardUpdate();
    }

    @Test
    void confirmCampaignDonation_ClearsLegacyRejectReason() {
        testDonation.setStatus(DonationStatus.PENDING);
        testDonation.setRejectReason("Legacy reject reason");

        when(donationRepository.findById(10L)).thenReturn(Optional.of(testDonation));
        when(campaignMemberService.canManageCampaign(1L, testUser)).thenReturn(true);
        when(donationRepository.save(any(Donation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DonationResponse response = donationService.confirmCampaignDonation(10L, testUser);

        assertNotNull(response);
        assertEquals(DonationStatus.SUCCESSFUL, testDonation.getStatus());
        assertNull(testDonation.getRejectReason());
        assertEquals(testUser, testDonation.getConfirmedBy());
        verify(notificationService).broadcastDashboardUpdate();
    }
}
