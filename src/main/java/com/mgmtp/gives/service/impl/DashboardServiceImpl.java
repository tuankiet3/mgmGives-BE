package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.dto.campaign.CampaignResponse;
import com.mgmtp.gives.dto.dashboard.ActivityDTO;
import com.mgmtp.gives.dto.dashboard.DashboardOverviewResponse;
import com.mgmtp.gives.dto.donation.DonationResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.Notification;
import com.mgmtp.gives.entity.Announcement;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.mapper.CampaignMapper;
import com.mgmtp.gives.repository.AnnouncementRepository;
import com.mgmtp.gives.repository.CampaignFollowerRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.repository.NotificationRepository;
import com.mgmtp.gives.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final CampaignRepository campaignRepository;
    private final DonationRepository donationRepository;
    private final NotificationRepository notificationRepository;
    private final AnnouncementRepository announcementRepository;
    private final CampaignFollowerRepository campaignFollowerRepository;
    private final CampaignMapper campaignMapper;

    @Override
    @Transactional(readOnly = true)
    public DashboardOverviewResponse getDashboardOverview(User currentUser) {
        log.info("Generating dashboard overview for user: {}", currentUser.getEmail());

        // 1. Total donated amount of current user (MONEY type, all statuses)
        List<Donation> userDonations = donationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());
        long totalDonatedAmount = userDonations.stream()
                .filter(d -> d.getType() == DonationType.MONEY)
                .mapToLong(d -> d.getAmount() != null ? d.getAmount() : 0L)
                .sum();

        // 2. Count Active Campaigns (APPROVED or IN_PROGRESS)
        long activeCampaignsCount = campaignRepository.countByStatusIn(
                List.of(CampaignStatus.APPROVED, CampaignStatus.IN_PROGRESS)
        );

        // 3. Count Completed Campaigns (COMPLETED)
        long completedCampaignsCount = campaignRepository.countByStatus(CampaignStatus.COMPLETED);

        // 3.5. Count Followed Campaigns for the current user
        long followedCampaignsCount = campaignFollowerRepository.countByUserId(currentUser.getId());

        // 4. Get Recommended Campaigns (Top 3 active campaigns: priority DESC, endDate ASC)
        Specification<Campaign> activeCampaignSpec = (root, query, cb) -> cb.and(
                cb.or(
                        cb.equal(root.get("status"), CampaignStatus.APPROVED),
                        cb.equal(root.get("status"), CampaignStatus.IN_PROGRESS)
                ),
                cb.greaterThan(root.get("endDate"), LocalDateTime.now())
        );
        Pageable recommendedPageable = PageRequest.of(0, 3, Sort.by(
                Sort.Order.asc("endDate")
        ));
        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
        List<CampaignResponse> recommendedCampaigns = campaignRepository.findAll(activeCampaignSpec, recommendedPageable)
                .getContent()
                .stream()
                .map(campaign -> campaignMapper.toResponse(campaign, currentUser.getId(), isAdmin))
                .toList();

        // 5. Get Recent Donations (Top 5)
        List<DonationResponse> recentDonations = userDonations.stream()
                .limit(5)
                .map(this::mapToDonationResponse)
                .toList();

        // 6. Get Recent Activities (Combine Notifications & Announcements)
        List<ActivityDTO> recentActivities = getCombinedActivities(currentUser);

        return DashboardOverviewResponse.builder()
                .totalDonatedAmount(totalDonatedAmount)
                .activeCampaignsCount(activeCampaignsCount)
                .followedCampaignsCount(followedCampaignsCount)
                .completedCampaignsCount(completedCampaignsCount)
                .recommendedCampaigns(recommendedCampaigns)
                .recentDonations(recentDonations)
                .recentActivities(recentActivities)
                .build();
    }

    private List<ActivityDTO> getCombinedActivities(User currentUser) {
        List<ActivityDTO> activities = new ArrayList<>();

        // Fetch user notifications
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());
        for (Notification n : notifications) {
            activities.add(ActivityDTO.builder()
                    .id("NOTI-" + n.getId())
                    .type("NOTIFICATION")
                    .title(n.getTitle())
                    .message(n.getMessage())
                    .linkUrl(n.getLinkUrl())
                    .createdAt(n.getCreatedAt() != null ? n.getCreatedAt() : LocalDateTime.now())
                    .build());
        }

        // Fetch published announcements
        List<Announcement> announcements = announcementRepository.findByOrderByPublishedAtDesc();
        for (Announcement a : announcements) {
            activities.add(ActivityDTO.builder()
                    .id("ANN-" + a.getId())
                    .type("ANNOUNCEMENT")
                    .title(a.getTitle())
                    .message(a.getContent())
                    .linkUrl(a.getCampaign() != null ? "/campaigns/" + a.getCampaign().getId() : null)
                    .createdAt(a.getPublishedAt() != null ? a.getPublishedAt() : 
                               (a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.now()))
                    .build());
        }

        // Sort combined list by createdAt DESC
        activities.sort(Comparator.comparing(ActivityDTO::getCreatedAt).reversed());

        // Limit to top 10 activities
        return activities.stream()
                .limit(10)
                .toList();
    }

    private DonationResponse mapToDonationResponse(Donation donation) {
        String donorName = "Anonymous";
        if (!donation.isAnonymous() && donation.getUser() != null) {
            donorName = donation.getUser().getFullName();
        }
        return DonationResponse.builder()
                .id(donation.getId())
                .campaignId(donation.getCampaign().getId())
                .campaignName(donation.getCampaign().getTitle())
                .donorName(donorName)
                .type(donation.getType())
                .amount(donation.getAmount())
                .detail(donation.getDetail())
                .isAnonymous(donation.isAnonymous())
                .status(donation.getStatus())
                .transactionId(donation.getTransactionId())
                .rejectReason(donation.getRejectReason())
                .message(donation.getMessage())
                .isMessageHidden(donation.isMessageHidden())
                .goodsCondition(donation.getGoodsCondition())
                .goodsCategory(donation.getGoodsCategory())
                .deliveryMethod(donation.getDeliveryMethod())
                .createdAt(donation.getCreatedAt())
                .build();
    }
}
