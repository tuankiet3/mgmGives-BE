package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.entity.UserPayOSConnection;
import com.mgmtp.gives.enums.DonationMethod;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.UserPayOSConnectionRepository;
import com.mgmtp.gives.service.TokenCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.payos.PayOS;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayOSClientProviderImplTest {

    @Mock
    private UserPayOSConnectionRepository userPayOSConnectionRepository;

    @Mock
    private TokenCryptoService tokenCryptoService;

    @Mock
    private PayOS defaultPayOS;

    @InjectMocks
    private PayOSClientProviderImpl payOSClientProvider;

    private Campaign testCampaign;
    private User testUser;
    private UserPayOSConnection testConnection;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("owner@example.com");

        testCampaign = new Campaign();
        testCampaign.setId(10L);
        testCampaign.setUser(testUser);

        testConnection = new UserPayOSConnection();
        testConnection.setId(100L);
        testConnection.setUser(testUser);
        testConnection.setClientId("encrypted-client-id");
        testConnection.setApiKey("encrypted-api-key");
        testConnection.setChecksumKey("encrypted-checksum-key");
    }

    @Test
    void getClientForCampaign_UseDefaultFallback_WhenNoOwner() {
        testCampaign.setUser(null);
        PayOS client = payOSClientProvider.getClientForCampaign(testCampaign);
        assertSame(defaultPayOS, client);
        verifyNoInteractions(userPayOSConnectionRepository);
    }

    @Test
    void getClientForCampaign_ThrowsException_WhenNoConnectionFound() {
        testCampaign.setDonationMethod(DonationMethod.PAYOS);
        when(userPayOSConnectionRepository.findByUserId(1L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                payOSClientProvider.getClientForCampaign(testCampaign));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Campaign owner does not have a connected PayOS account."));
    }

    @Test
    void getClientForCampaign_UseDefaultFallback_WhenNoConnectionFoundAndManualQR() {
        testCampaign.setDonationMethod(DonationMethod.MANUAL_QR);
        when(userPayOSConnectionRepository.findByUserId(1L)).thenReturn(Optional.empty());
        PayOS client = payOSClientProvider.getClientForCampaign(testCampaign);
        assertSame(defaultPayOS, client);
    }

    @Test
    void getClientForCampaign_UseCustomCredentials_WhenConnectionExists() {
        when(userPayOSConnectionRepository.findByUserId(1L)).thenReturn(Optional.of(testConnection));
        when(tokenCryptoService.decrypt("encrypted-client-id")).thenReturn("decrypted-client-id");
        when(tokenCryptoService.decrypt("encrypted-api-key")).thenReturn("decrypted-api-key");
        when(tokenCryptoService.decrypt("encrypted-checksum-key")).thenReturn("decrypted-checksum-key");

        PayOS client = payOSClientProvider.getClientForCampaign(testCampaign);

        assertNotNull(client);
        assertNotSame(defaultPayOS, client);
        // We can't easily inspect vn.payos.PayOS internal state but verifies decryption calls were made
        verify(tokenCryptoService).decrypt("encrypted-client-id");
        verify(tokenCryptoService).decrypt("encrypted-api-key");
        verify(tokenCryptoService).decrypt("encrypted-checksum-key");
    }
}
