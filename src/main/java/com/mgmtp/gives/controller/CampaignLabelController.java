package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.campaign_label.CampaignLabelResponse;
import com.mgmtp.gives.dto.campaign_label.CreateCampaignLabelRequest;
import com.mgmtp.gives.dto.campaign_label.UpdateCampaignLabelRequest;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.CampaignLabelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Campaign Labels", description = "Endpoints for campaign label management")
public class CampaignLabelController {

        private final CampaignLabelService campaignLabelService;

        @PostMapping("/campaigns/{campaignId}/labels")
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Create a new label for a campaign")
        public ApiResponse<CampaignLabelResponse> createLabel(
                        @PathVariable Long campaignId,
                        @Valid @RequestBody CreateCampaignLabelRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to create label: campaignId={}, userId={}",
                                campaignId, userDetails.getUser().getId());
                return ApiResponse.success(
                                campaignLabelService.createLabel(campaignId, request, userDetails.getUser()),
                                "Label created successfully");
        }

        @GetMapping("/campaigns/{campaignId}/labels")
        @Operation(summary = "Get all labels for a campaign")
        public ApiResponse<List<CampaignLabelResponse>> getLabels(
                        @PathVariable Long campaignId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to get labels: campaignId={}", campaignId);
                return ApiResponse.success(
                                campaignLabelService.getLabelsByCampaign(campaignId, userDetails.getUser()));
        }

        @RequestMapping(value = "/labels/{labelId}", method = { RequestMethod.PUT, RequestMethod.PATCH })
        @Operation(summary = "Update an existing label")
        public ApiResponse<CampaignLabelResponse> updateLabel(
                        @PathVariable Long labelId,
                        @Valid @RequestBody UpdateCampaignLabelRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to update label: labelId={}, userId={}",
                                labelId, userDetails.getUser().getId());
                return ApiResponse.success(
                                campaignLabelService.updateLabel(labelId, request, userDetails.getUser()),
                                "Label updated successfully");
        }

        @DeleteMapping("/labels/{labelId}")
        @Operation(summary = "Delete a label")
        public ApiResponse<Void> deleteLabel(
                        @PathVariable Long labelId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to delete label: labelId={}, userId={}",
                                labelId, userDetails.getUser().getId());
                campaignLabelService.deleteLabel(labelId, userDetails.getUser());
                return ApiResponse.success(null, "Label deleted successfully");
        }
}
