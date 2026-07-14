package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.campaign.CampaignResultGenerateResponse;
import com.mgmtp.gives.dto.campaign.CampaignResultRequest;
import com.mgmtp.gives.dto.campaign.CampaignResultResponse;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.CampaignResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaign Result", description = "Endpoints for posting and viewing campaign final results")
public class CampaignResultController {

    private final CampaignResultService campaignResultService;

    @PostMapping("/{id}/result")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post final result", description = "Campaign Admin posts the final result on a COMPLETED campaign.")
    public ApiResponse<CampaignResultResponse> postResult(
            @PathVariable Long id,
            @Valid @RequestBody CampaignResultRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to post campaign result: campaignId={}, userId={}", id, userDetails.getUser().getId());
        return ApiResponse.success(
                campaignResultService.postResult(id, request, userDetails.getUser()),
                "Campaign result posted successfully");
    }

    @PutMapping("/{id}/result")
    @Operation(summary = "Update final result", description = "Campaign Admin edits the posted final result.")
    public ApiResponse<CampaignResultResponse> updateResult(
            @PathVariable Long id,
            @Valid @RequestBody CampaignResultRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to update campaign result: campaignId={}, userId={}", id, userDetails.getUser().getId());
        return ApiResponse.success(
                campaignResultService.updateResult(id, request, userDetails.getUser()),
                "Campaign result updated successfully");
    }

    @GetMapping("/{id}/result")
    @Operation(summary = "Get final result", description = "Returns the posted final result including computed stats. Visible to all authenticated users.")
    public ApiResponse<CampaignResultResponse> getResult(@PathVariable Long id) {
        log.info("REST request to get campaign result: campaignId={}", id);
        return ApiResponse.success(campaignResultService.getResult(id));
    }

    @GetMapping("/{id}/result/pdf")
    @Operation(summary = "View final result as PDF", description = "Renders the posted final result as a PDF, opened inline (e.g. in a new browser tab) rather than force-downloaded. Visible to all authenticated users.")
    public ResponseEntity<byte[]> exportResultPdf(@PathVariable Long id) {
        log.info("REST request to export campaign result PDF: campaignId={}", id);
        byte[] pdf = campaignResultService.generateResultPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("final-report-" + id + ".pdf").build().toString())
                .body(pdf);
    }

    @PostMapping("/{id}/result/generate")
    @Operation(summary = "Generate result draft with AI", description = "Uses Gemini AI to generate a draft for resultSummary, itemsSummary, and acknowledgements. Campaign Admin only.")
    public ApiResponse<CampaignResultGenerateResponse> generateResultDraft(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to generate AI result draft: campaignId={}, userId={}", id, userDetails.getUser().getId());
        return ApiResponse.success(
                campaignResultService.generateResultDraft(id, userDetails.getUser()),
                "Result draft generated successfully");
    }
}
