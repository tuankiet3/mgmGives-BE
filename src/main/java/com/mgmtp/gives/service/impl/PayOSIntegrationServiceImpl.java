package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.dto.integration.PayOSConnectionRequest;
import com.mgmtp.gives.dto.integration.PayOSConnectionStatusResponse;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.entity.UserPayOSConnection;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.repository.UserPayOSConnectionRepository;
import com.mgmtp.gives.service.PayOSIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOSIntegrationServiceImpl implements PayOSIntegrationService {

    private final UserPayOSConnectionRepository userPayOSConnectionRepository;
    private final com.mgmtp.gives.service.TokenCryptoService tokenCryptoService;
    private final com.mgmtp.gives.service.NotificationService notificationService;

    @Override
    public PayOSConnectionStatusResponse getStatus(User user) {
        Optional<UserPayOSConnection> connection = userPayOSConnectionRepository.findByUserId(user.getId());
        if (connection.isEmpty()) {
            return new PayOSConnectionStatusResponse(false, null, null);
        }
        UserPayOSConnection conn = connection.get();
        String decryptedClientId = tokenCryptoService.decrypt(conn.getClientId());
        String maskedClientId = decryptedClientId.length() > 8
                ? decryptedClientId.substring(0, 8) + "********"
                : decryptedClientId;
        return new PayOSConnectionStatusResponse(true, maskedClientId, conn.getCreatedAt());
    }

    @Override
    @Transactional
    public void connect(User user, PayOSConnectionRequest request) {
        log.info("Connecting PayOS for user: {}", user.getEmail());

        verifyPayOSCredentials(request);

        UserPayOSConnection connection = userPayOSConnectionRepository.findByUserId(user.getId())
                .orElse(new UserPayOSConnection());

        connection.setUser(user);
        connection.setClientId(tokenCryptoService.encrypt(request.clientId().trim()));
        connection.setApiKey(tokenCryptoService.encrypt(request.apiKey().trim()));
        connection.setChecksumKey(tokenCryptoService.encrypt(request.checksumKey().trim()));
        connection.setUpdatedAt(LocalDateTime.now());

        userPayOSConnectionRepository.save(connection);

        sendPayOSNotification(user, "PayOS Connected", "You have successfully connected your PayOS account.");
    }

    @Override
    @Transactional
    public void disconnect(User user) {
        log.info("Disconnecting PayOS for user: {}", user.getEmail());
        userPayOSConnectionRepository.deleteByUserId(user.getId());
        sendPayOSNotification(user, "PayOS Disconnected", "You have disconnected your PayOS account.");
    }

    private void sendPayOSNotification(User user, String title, String message) {
        try {
            com.mgmtp.gives.dto.notification.NotificationRecipient recipient =
                    new com.mgmtp.gives.dto.notification.NotificationRecipient(user.getId(), user.getEmail());
            com.mgmtp.gives.dto.notification.CreateNotificationCommand command =
                    com.mgmtp.gives.dto.notification.CreateNotificationCommand.builder()
                            .recipients(java.util.Set.of(recipient))
                            .type(com.mgmtp.gives.enums.NotificationType.DONATION)
                            .title(title)
                            .message(message)
                            .linkUrl("/settings")
                            .build();
            notificationService.createNotification(command);
        } catch (Exception e) {
            log.error("Failed to send PayOS connection notification to user: {}", user.getEmail(), e);
        }
    }

    private void verifyPayOSCredentials(PayOSConnectionRequest request) {
        try {
            if (request.clientId() == null || request.clientId().trim().isEmpty() ||
                request.apiKey() == null || request.apiKey().trim().isEmpty() ||
                request.checksumKey() == null || request.checksumKey().trim().isEmpty()) {
                throw new IllegalArgumentException("Client ID, API Key, and Checksum Key must not be empty.");
            }
            PayOS testPayOS = new PayOS(request.clientId().trim(), request.apiKey().trim(), request.checksumKey().trim());
            testPayOS.paymentRequests().get(1L);
            log.info("Credentials verification: Connection verified (found payment link).");
        } catch (Exception e) {
            String msg = e.getMessage();
            log.info("Credentials check result message: {}", msg);
            if (msg != null && (
                    msg.contains("Không tìm thấy") ||
                    msg.contains("Payment link not found") ||
                    msg.contains("20")
            )) {
                log.info("Credentials verification: Connection verified (Payment link not found is expected).");
                return;
            }
            log.error("PayOS Credentials validation failed", e);
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid PayOS credentials or service unreachable. Details: " + msg);
        }
    }
}
