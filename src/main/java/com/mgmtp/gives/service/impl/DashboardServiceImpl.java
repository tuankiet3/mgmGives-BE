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
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.mapper.CampaignMapper;
import com.mgmtp.gives.repository.AnnouncementRepository;
import com.mgmtp.gives.repository.CampaignFollowerRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.NotificationType;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.NotificationRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.UserRepository;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.dto.dashboard.AdminDashboardOverviewResponse;
import com.mgmtp.gives.service.DashboardService;
import lombok.RequiredArgsConstructor;
import java.util.stream.Collectors;
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
import org.jsoup.Jsoup;

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
    private final CampaignMediaRepository campaignMediaRepository;
    private final CampaignMemberRepository campaignMemberRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardOverviewResponse getDashboardOverview(User currentUser) {
        log.info("Generating dashboard overview for user: {}", currentUser.getEmail());

        // 1. Total donated amount of current user (MONEY type, successful status) - Database aggregated to avoid unbounded fetch
        long totalDonatedAmount = donationRepository.sumAmountByUserIdAndStatusAndType(
                currentUser.getId(),
                DonationStatus.SUCCESSFUL,
                DonationType.MONEY
        );

        // 2. Count Completed Campaigns (COMPLETED) that current user joined as VOLUNTEER
        long completedCampaignsCount = campaignMemberRepository.countByUserIdAndCampaignStatusAndRoleInCampaign(
                currentUser.getId(),
                CampaignStatus.COMPLETED,
                CampaignMemberRole.VOLUNTEER
        );

        // 3. Count Followed Campaigns for the current user
        long followedCampaignsCount = campaignFollowerRepository.countByUserId(currentUser.getId());

        // 4. Get Recommended Campaigns (Top 3 active campaigns: closest endDate first, then highest priority)
        Specification<Campaign> activeCampaignSpec = (root, query, cb) -> cb.and(
                cb.or(
                        cb.equal(root.get("status"), CampaignStatus.APPROVED),
                        cb.equal(root.get("status"), CampaignStatus.IN_PROGRESS)
                ),
                cb.greaterThan(root.get("endDate"), LocalDateTime.now())
        );
        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;

        Pageable recommendedPageable = PageRequest.of(0, 3, Sort.by(
                Sort.Order.asc("endDate"),
                Sort.Order.desc("priority")
        ));
        List<Campaign> campaigns = campaignRepository.findAll(activeCampaignSpec, recommendedPageable).getContent();
                
        List<Long> campaignIds = campaigns.stream().map(Campaign::getId).toList();
        List<CampaignMedia> coverImages = campaignIds.isEmpty()
                ? List.of()
                : campaignMediaRepository.findCoverImagesByCampaignIds(campaignIds);
        java.util.Map<Long, String> coverImageMap = coverImages.stream()
                .collect(Collectors.toMap(
                        m -> m.getCampaign().getId(),
                        CampaignMedia::getUrl,
                        (existing, replacement) -> existing));

        List<CampaignResponse> recommendedCampaigns = campaigns.stream()
                .map(campaign -> {
                    CampaignResponse response = campaignMapper.toResponse(campaign, currentUser.getId(), isAdmin);
                    response.setCoverImageUrl(coverImageMap.get(campaign.getId()));
                    return response;
                })
                .toList();

        // 5. Get Recent Donations (Top 10) - Limited fetch from database
        List<Donation> recentUserDonations = donationRepository.findTop10ByUserIdOrderByCreatedAtDesc(currentUser.getId());
        List<DonationResponse> recentDonations = recentUserDonations.stream()
                .map(d -> mapToDonationResponse(d, currentUser))
                .toList();

        // 6. Get Recent Activities (Combine Notifications & Announcements)
        List<ActivityDTO> recentActivities = getCombinedActivities(currentUser);

        return DashboardOverviewResponse.builder()
                .totalDonatedAmount(totalDonatedAmount)
                .followedCampaignsCount(followedCampaignsCount)
                .completedCampaignsCount(completedCampaignsCount)
                .recommendedCampaigns(recommendedCampaigns)
                .recentDonations(recentDonations)
                .recentActivities(recentActivities)
                .build();
    }

    private List<ActivityDTO> getCombinedActivities(User currentUser) {
        List<ActivityDTO> activities = new ArrayList<>();

        // 1. Fetch user notifications (synchronous with header, filtering/mapping announcements properly)
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId(), PageRequest.of(0, 10)).getContent();
        for (Notification n : notifications) {
            activities.add(ActivityDTO.builder()
                    .id("NOTI-" + n.getId())
                    .type(n.getType() == NotificationType.CAMPAIGN_ANNOUNCEMENT ? "ANNOUNCEMENT" : "NOTIFICATION")
                    .title(n.getTitle())
                    .message(n.getMessage())
                    .linkUrl(n.getLinkUrl())
                    .createdAt(n.getCreatedAt() != null ? n.getCreatedAt() : LocalDateTime.now())
                    .build());
        }

        // 2. Fetch announcements created by this user (so creator can also see them)
        List<Announcement> myAnnouncements = announcementRepository.findByCreatedByIdOrderByPublishedAtDesc(currentUser.getId(), PageRequest.of(0, 10));
        for (Announcement a : myAnnouncements) {
            String snippet = getPlainTextSnippet(a.getContent());
            String message = a.getTitle();
            if (!snippet.isEmpty()) {
                message = a.getTitle() + " - " + snippet;
            }
            activities.add(ActivityDTO.builder()
                    .id("ANN-" + a.getId())
                    .type("ANNOUNCEMENT")
                    .title("New announcement in \"" + a.getCampaign().getTitle() + "\"")
                    .message(message)
                    .linkUrl(a.getCampaign() != null ? "/campaigns/" + a.getCampaign().getId() + "/announcements/" + a.getId() : null)
                    .createdAt(a.getPublishedAt() != null ? a.getPublishedAt() : 
                               (a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.now()))
                    .build());
        }

        // 3. Fetch donations of campaigns followed or joined by this user
        List<Donation> donations = donationRepository.findDonationsForFollowedAndJoinedCampaigns(currentUser.getId(), PageRequest.of(0, 10));
        boolean isSystemAdmin = currentUser.getRole() == com.mgmtp.gives.enums.UserRole.ADMIN;

        // Batch fetch campaign admin permissions to avoid querying inside a loop (N+1 query problem)
        List<Long> campaignIds = donations.stream()
                .map(d -> d.getCampaign().getId())
                .distinct()
                .toList();
        List<Long> adminCampaignIds = campaignIds.isEmpty() ? List.of() :
                campaignMemberRepository.findCampaignIdsByUserIdAndRoleAndCampaignIdsIn(
                        currentUser.getId(),
                        com.mgmtp.gives.enums.CampaignMemberRole.CAMPAIGN_ADMIN,
                        campaignIds
                );

        for (Donation d : donations) {
            String donorName = "Anonymous";
            if (!d.isAnonymous() && d.getUser() != null) {
                donorName = d.getUser().getFullName();
            }

            // Check if user has permission to see the amount (is self, global admin, creator, or campaign admin)
            boolean isSelf = d.getUser() != null && d.getUser().getId().equals(currentUser.getId());
            boolean isCreator = d.getCampaign().getUser() != null && d.getCampaign().getUser().getId().equals(currentUser.getId());
            boolean isCampaignAdmin = adminCampaignIds.contains(d.getCampaign().getId());
            boolean canSeeAmount = isSelf || isSystemAdmin || isCreator || isCampaignAdmin;

            String message = "";
            if (d.getType() == com.mgmtp.gives.enums.DonationType.MONEY) {
                String amountStr = canSeeAmount 
                        ? (d.getAmount() != null ? String.format("%,d", d.getAmount().longValue()) : "0") 
                        : "****";
                message = donorName + " donated " + amountStr + " VND";
            } else if (d.getType() == com.mgmtp.gives.enums.DonationType.GOODS) {
                message = donorName + " donated goods: " + (d.getGoodsCategory() != null ? d.getGoodsCategory() : "");
            }

            activities.add(ActivityDTO.builder()
                    .id("DON-" + d.getId())
                    .type("DONATION")
                    .title(d.getCampaign().getTitle())
                    .message(message)
                    .linkUrl("/campaigns/" + d.getCampaign().getId())
                    .createdAt(d.getCreatedAt() != null ? d.getCreatedAt() : LocalDateTime.now())
                    .build());
        }

        // Sort combined list by createdAt DESC
        activities.sort(Comparator.comparing(ActivityDTO::getCreatedAt).reversed());

        // Limit to top 10 activities
        return activities.stream()
                .limit(10)
                .toList();
    }

    private DonationResponse mapToDonationResponse(Donation donation, User currentUser) {
        String donorName = "Anonymous";
        if (!donation.isAnonymous() && donation.getUser() != null) {
            donorName = donation.getUser().getFullName();
        }
        
        boolean isAdmin = currentUser.getRole() == com.mgmtp.gives.enums.UserRole.ADMIN;
        boolean isCreator = donation.getCampaign().getUser() != null &&
                donation.getCampaign().getUser().getId().equals(currentUser.getId());
        boolean isDonor = donation.getUser() != null && donation.getUser().getId().equals(currentUser.getId());
        Long amountVal = (isAdmin || isCreator || isDonor) ? donation.getAmount() : null;

        return DonationResponse.builder()
                .id(donation.getId())
                .campaignId(donation.getCampaign().getId())
                .campaignName(donation.getCampaign().getTitle())
                .donorName(donorName)
                .type(donation.getType())
                .amount(amountVal)
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

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardOverviewResponse getAdminDashboardOverview() {
        log.info("Generating admin dashboard overview statistics");

        long totalEmployees = userRepository.count();
        long campaignAdmins = userRepository.countByRole(com.mgmtp.gives.enums.UserRole.ADMIN);
        long totalCampaigns = campaignRepository.countByStatusNot(CampaignStatus.DRAFT);
        long pendingCampaigns = campaignRepository.countByStatus(CampaignStatus.PENDING);
        long activeCampaigns = campaignRepository.countByStatusIn(
                List.of(CampaignStatus.APPROVED, CampaignStatus.IN_PROGRESS)
        );

        int currentYear = java.time.LocalDate.now().getYear();
        LocalDateTime startOfYear = LocalDateTime.of(currentYear, 1, 1, 0, 0, 0);
        LocalDateTime endOfYear = LocalDateTime.of(currentYear, 12, 31, 23, 59, 59);
        long totalDonation = donationRepository.sumConfirmedDonationsByYear(
                DonationStatus.SUCCESSFUL,
                DonationType.MONEY,
                startOfYear,
                endOfYear
        );

        return AdminDashboardOverviewResponse.builder()
                .totalEmployees(totalEmployees)
                .campaignAdmins(campaignAdmins)
                .totalCampaigns(totalCampaigns)
                .pendingCampaigns(pendingCampaigns)
                .activeCampaigns(activeCampaigns)
                .totalDonation(totalDonation)
                .build();
    }

    private String getPlainTextSnippet(String html) {
        if (html == null) {
            return "";
        }
        String text = Jsoup.parse(html).text();
        if (text.length() > 120) {
            return text.substring(0, 117) + "...";
        }
        return text;
    }
}
