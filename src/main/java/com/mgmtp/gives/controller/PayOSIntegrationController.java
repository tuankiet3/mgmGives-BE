package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.integration.PayOSConnectionRequest;
import com.mgmtp.gives.dto.integration.PayOSConnectionStatusResponse;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.PayOSIntegrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/payos")
@RequiredArgsConstructor
@Slf4j
public class PayOSIntegrationController {

    private final PayOSIntegrationService payOSIntegrationService;

    @GetMapping("/status")
    public ApiResponse<PayOSConnectionStatusResponse> status(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("PayOS status check requested by user: {}", userDetails.getUser().getEmail());
        PayOSConnectionStatusResponse status = payOSIntegrationService.getStatus(userDetails.getUser());
        return ApiResponse.success(status);
    }

    @PostMapping("/connect")
    public ApiResponse<Void> connect(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PayOSConnectionRequest request
    ) {
        log.info("PayOS connect requested by user: {}", userDetails.getUser().getEmail());
        payOSIntegrationService.connect(userDetails.getUser(), request);
        return ApiResponse.success(null, "PayOS connected successfully");
    }

    @PostMapping("/disconnect")
    public ApiResponse<Void> disconnect(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("PayOS disconnect requested by user: {}", userDetails.getUser().getEmail());
        payOSIntegrationService.disconnect(userDetails.getUser());
        return ApiResponse.success(null, "PayOS disconnected successfully");
    }
}
