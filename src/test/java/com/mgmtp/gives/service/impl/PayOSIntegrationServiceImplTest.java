package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.integration.PayOSConnectionRequest;
import com.mgmtp.gives.dto.integration.PayOSConnectionStatusResponse;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.entity.UserPayOSConnection;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.UserPayOSConnectionRepository;
import com.mgmtp.gives.service.NotificationService;
import com.mgmtp.gives.service.TokenCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayOSIntegrationServiceImplTest {

    @Mock
    private UserPayOSConnectionRepository userPayOSConnectionRepository;

    @Mock
    private TokenCryptoService tokenCryptoService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CampaignRepository campaignRepository;

    @InjectMocks
    private PayOSIntegrationServiceImpl payOSIntegrationService;

    private User testUser;
    private UserPayOSConnection testConnection;
    private PayOSConnectionRequest request;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("user@example.com");

        testConnection = new UserPayOSConnection();
        testConnection.setId(100L);
        testConnection.setUser(testUser);
        testConnection.setClientId("encrypted-client-id");
        testConnection.setCreatedAt(LocalDateTime.now());

        request = new PayOSConnectionRequest("test-client-id", "test-api-key", "test-checksum-key");
    }

    @Test
    void getStatus_Disconnected_WhenNoConnection() {
        when(userPayOSConnectionRepository.findByUserId(1L)).thenReturn(Optional.empty());

        PayOSConnectionStatusResponse response = payOSIntegrationService.getStatus(testUser);

        assertFalse(response.connected());
        assertNull(response.clientId());
        assertNull(response.connectedAt());
    }

    @Test
    void getStatus_Connected_WhenConnectionExists() {
        when(userPayOSConnectionRepository.findByUserId(1L)).thenReturn(Optional.of(testConnection));
        when(tokenCryptoService.decrypt("encrypted-client-id")).thenReturn("decrypted-client-id-value");

        PayOSConnectionStatusResponse response = payOSIntegrationService.getStatus(testUser);

        assertTrue(response.connected());
        assertEquals("decrypte********", response.clientId());
        assertNotNull(response.connectedAt());
    }

    @Test
    void disconnect_DeletesConnectionAndSendsNotification() {
        when(campaignRepository.existsByUserIdAndStatusInAndDonationMethodIn(eq(1L), any(), any())).thenReturn(false);

        payOSIntegrationService.disconnect(testUser);

        verify(userPayOSConnectionRepository).deleteByUserId(1L);
        verify(notificationService).createNotification(any());
    }

    @Test
    void disconnect_ThrowsException_WhenActiveCampaignsExist() {
        when(campaignRepository.existsByUserIdAndStatusInAndDonationMethodIn(eq(1L), any(), any())).thenReturn(true);

        AppException exception = assertThrows(AppException.class, () ->
                payOSIntegrationService.disconnect(testUser));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Cannot disconnect PayOS while you have active or pending campaigns"));
        verifyNoInteractions(userPayOSConnectionRepository);
    }

    @Test
    void connect_Fails_WhenValidationFails() {
        // Here we simulate validation failure by providing invalid credentials (or causing mock to throw an exception that is not whitelisted)
        // Since vn.payos.PayOS is initialized under the hood, we can pass keys that will throw errors.
        // Let's pass blank client/keys which causes vn.payos.PayOS constructor to throw an IllegalArgumentException.
        PayOSConnectionRequest invalidRequest = new PayOSConnectionRequest("", "", "");

        AppException exception = assertThrows(AppException.class, () ->
                payOSIntegrationService.connect(testUser, invalidRequest));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Invalid PayOS credentials"));
        verifyNoInteractions(userPayOSConnectionRepository);
    }
}
