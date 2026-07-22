package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.campaign.AdminCampaignResponse;
import com.mgmtp.gives.dto.campaign.RejectCampaignRequest;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.mapper.AdminCampaignMapper;
import com.mgmtp.gives.mapper.CampaignMediaMapper;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.AdminCampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/campaigns")
@RequiredArgsConstructor
@Tag(name = "Admin Campaign Review", description = "Endpoints for admin to review, approve, and reject campaigns")
public class AdminCampaignController {

        private final AdminCampaignService adminCampaignService;
        private final AdminCampaignMapper adminCampaignMapper;
        private final CampaignMediaMapper campaignMediaMapper;

        @GetMapping
        @Operation(summary = "List campaigns for review", description = "Lists campaigns filtered by status, category, and keyword. Sorted newest first by default.")
        public ApiResponse<PageResponse<AdminCampaignResponse>> getCampaigns(
                        @Parameter(description = "Campaign status to filter by") @RequestParam(required = false) CampaignStatus status,
                        @Parameter(description = "Category ID to filter by") @RequestParam(value = "categoryId", required = false) List<Long> categoryId,
                        @Parameter(description = "Category IDs to filter by") @RequestParam(required = false) List<Long> categoryIds,
                        @Parameter(description = "Keyword to search by") @RequestParam(required = false) String keyword,
                        @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
                List<Long> mergedCategoryIds = mergeCategoryIds(categoryId, categoryIds);
                log.info("REST admin request to list campaigns: status={}, categoryIds={}, keyword={}", status,
                                mergedCategoryIds, keyword);
                Page<Campaign> page = adminCampaignService.getCampaigns(status, mergedCategoryIds, keyword, pageable);
                List<AdminCampaignResponse> dtoList = page.getContent().stream()
                                .map(adminCampaignMapper::toAdminResponse)
                                .collect(Collectors.toList());
                return ApiResponse.success(PageResponse.of(page, dtoList));
        }

        private List<Long> mergeCategoryIds(List<Long> categoryId, List<Long> categoryIds) {
                List<Long> merged = new ArrayList<>();
                if (categoryId != null) {
                        merged.addAll(categoryId);
                }
                if (categoryIds != null) {
                        merged.addAll(categoryIds);
                }
                return merged.stream().distinct().toList();
        }

        @GetMapping("/{id}")
        @Operation(summary = "Get campaign details for review", description = "Returns full campaign details including submitter info and review metadata.")
        public ApiResponse<AdminCampaignResponse> getCampaignById(
                        @Parameter(description = "The ID of the campaign", required = true) @PathVariable Long id) {
                log.info("REST admin request to get campaign details: id={}", id);
                Campaign campaign = adminCampaignService.getCampaignById(id);
                AdminCampaignResponse response = adminCampaignMapper.toAdminResponse(campaign);
                response.setMedias(campaignMediaMapper.toResponseList(
                                adminCampaignService.getActiveMediasByCampaignId(id)));
                return ApiResponse.success(response);
        }

        @PutMapping("/{id}/approve")
        @Operation(summary = "Approve a pending campaign", description = "Sets campaign status to APPROVED and auto-promotes the submitter to Campaign Admin.")
        public ApiResponse<AdminCampaignResponse> approveCampaign(
                        @Parameter(description = "The ID of the campaign to approve", required = true) @PathVariable Long id,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST admin request to approve campaign: id={}, adminId={}", id,
                                userDetails.getUser().getId());
                Campaign campaign = adminCampaignService.approveCampaign(id, userDetails.getUser());
                return ApiResponse.success(
                                adminCampaignMapper.toAdminResponse(campaign),
                                "Campaign approved successfully");
        }

        @PutMapping("/{id}/reject")
        @Operation(summary = "Reject a pending campaign", description = "Sets campaign status to REJECTED with a mandatory rejection reason.")
        public ApiResponse<AdminCampaignResponse> rejectCampaign(
                        @Parameter(description = "The ID of the campaign to reject", required = true) @PathVariable Long id,
                        @Valid @RequestBody RejectCampaignRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST admin request to reject campaign: id={}, adminId={}, reason='{}'", id,
                                userDetails.getUser().getId(), request.reason());
                Campaign campaign = adminCampaignService.rejectCampaign(
                                id, request.reason(), userDetails.getUser());
                return ApiResponse.success(
                                adminCampaignMapper.toAdminResponse(campaign),
                                "Campaign rejected successfully");
        }

}
