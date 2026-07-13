package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.dto.webex.WebexAuthorizeUrlResponse;
import com.mgmtp.gives.dto.webex.WebexConnectionStatusResponse;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.UserWebexConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/integrations/webex")
@RequiredArgsConstructor
@Slf4j
public class WebexOAuthController {

    private final UserWebexConnectionService userWebexConnectionService;
    private final MailProps mailProps;

    @GetMapping("/oauth/authorize")
    public ApiResponse<WebexAuthorizeUrlResponse> authorize(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String returnTo
    ) {
        log.info("Webex OAuth authorize requested. userId={}", userDetails.getUser().getId());
        return ApiResponse.success(userWebexConnectionService.createAuthorizeUrl(userDetails.getUser(), returnTo));
    }

    @GetMapping("/oauth/callback")
    public RedirectView callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription
    ) {
        log.info("Webex OAuth callback received. hasCode={}, hasState={}, error={}",
                code != null && !code.isBlank(),
                state != null && !state.isBlank(),
                error);
        if (error != null) {
            log.warn("Webex OAuth callback returned error. error={}, description={}", error, errorDescription);
            return redirect("error", errorDescription != null ? errorDescription : "Webex authorization failed: " + error);
        }
        if (code == null || code.isBlank()) {
            log.warn("Webex OAuth callback missing code. statePresent={}", state != null && !state.isBlank());
            return redirect("error", "Missing Webex authorization code");
        }

        try {
            String redirectUrl = userWebexConnectionService.handleCallback(code, state);
            log.info("Webex OAuth callback completed successfully. redirectUrl={}", redirectUrl);
            return new RedirectView(redirectUrl);
        } catch (AppException ex) {
            log.warn("Webex OAuth callback failed. message={}", ex.getMessage());
            return redirect("error", ex.getMessage());
        }
    }

    @GetMapping("/status")
    public ApiResponse<WebexConnectionStatusResponse> status(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        WebexConnectionStatusResponse response = userWebexConnectionService.getStatus(userDetails.getUser());
        log.info("Webex connection status requested. userId={}, connected={}, webexEmail={}",
                userDetails.getUser().getId(), response.connected(), response.webexEmail());
        return ApiResponse.success(response);
    }

    @DeleteMapping
    public ApiResponse<Void> disconnect(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("Webex disconnect requested. userId={}", userDetails.getUser().getId());
        userWebexConnectionService.disconnect(userDetails.getUser());
        return ApiResponse.success(null, "Webex disconnected successfully");
    }

    private RedirectView redirect(String status, String message) {
        String url = UriComponentsBuilder
                .fromUriString(mailProps.getFrontendUrl())
                .path("/integration-settings")
                .queryParam("webex", status)
                .queryParam("message", message)
                .toUriString();
        return new RedirectView(url);
    }
}
