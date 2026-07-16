package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.dto.campaign.CampaignResultDraftContext;
import com.mgmtp.gives.dto.campaign.CampaignResultGenerateResponse;
import com.mgmtp.gives.dto.campaign.CampaignResultRequest;
import com.mgmtp.gives.dto.campaign.CampaignResultResponse;
import com.mgmtp.gives.dto.campaign.DonorNotificationInfo;
import com.mgmtp.gives.dto.campaign.DonorThankYouContext;
import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.Announcement;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.entity.CampaignTask;
import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.TaskAssignment;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.enums.MediaContext;
import com.mgmtp.gives.enums.NotificationType;
import com.mgmtp.gives.enums.TaskStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.mapper.CampaignMediaMapper;
import com.mgmtp.gives.repository.AnnouncementRepository;
import com.mgmtp.gives.repository.CampaignFollowerRepository;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.CampaignTaskRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.specification.CampaignTaskSpecifications;
import com.mgmtp.gives.service.CampaignMemberService;
import com.mgmtp.gives.service.CampaignResultService;
import com.mgmtp.gives.service.EmailService;
import com.mgmtp.gives.service.GeminiService;
import com.mgmtp.gives.service.MediaService;
import com.mgmtp.gives.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.mgmtp.gives.enums.DonationStatus;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignResultServiceImpl implements CampaignResultService {

    private static final String RESULT_EMAIL_SUBJECT_PREFIX = "Campaign Results: ";
    private static final DateTimeFormatter ANNOUNCEMENT_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final String PDF_FONT_FAMILY = "Noto Sans";
    private static final int PDF_IMAGE_MAX_DIMENSION = 640;
    private static final float PDF_IMAGE_JPEG_QUALITY = 0.7f;
    // Task descriptions can be up to 5000 chars (CreateCampaignTaskRequest), far larger than
    // any reasonable per-task prompt budget — truncated here, at the source, so a long
    // description can never crowd out this task's own status/assignee fields downstream.
    private static final int MAX_TASK_DESCRIPTION_LENGTH = 250;

    private final CampaignRepository campaignRepository;
    private final CampaignMemberRepository campaignMemberRepository;
    private final CampaignMemberService campaignMemberService;
    private final DonationRepository donationRepository;
    private final AnnouncementRepository announcementRepository;
    private final GeminiService geminiService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final CampaignFollowerRepository campaignFollowerRepository;
    private final TemplateEngine templateEngine;
    private final CampaignMediaRepository campaignMediaRepository;
    private final CampaignMediaMapper campaignMediaMapper;
    private final CampaignTaskRepository campaignTaskRepository;
    private final MediaService mediaService;
    private final MailProps mailProps;

    @Value("${app.media.upload-dir}")
    private String uploadDir;

    @Override
    @Transactional
    public CampaignResultResponse postResult(Long campaignId, CampaignResultRequest request, User currentUser) {
        Campaign campaign = findAndValidateCampaign(campaignId);
        checkAuthorized(campaign, currentUser);

        if (campaign.isResultPosted()) {
            log.warn("Attempt to re-post result for already-posted campaign: campaignId={}", campaignId);
            throw new AppException(ErrorCode.CAMPAIGN_RESULT_ALREADY_POSTED);
        }

        long confirmedTotal = donationRepository.sumConfirmedAmountByCampaignId(campaignId);
        long finalAmount = request.finalAmountRaised() != null ? request.finalAmountRaised() : confirmedTotal;

        long donorCount = donationRepository.countDistinctDonorsByCampaignId(campaignId);
        long volunteerCount = campaignMemberRepository.countByCampaignIdAndRoleInCampaign(
                campaignId, CampaignMemberRole.VOLUNTEER);

        campaign.setResultSummary(request.resultSummary());
        campaign.setFinalAmountRaised(finalAmount);
        campaign.setItemsSummary(request.itemsSummary());
        campaign.setAcknowledgements(request.acknowledgements());
        campaign.setTaskSummary(request.taskSummary());
        campaign.setResultPosted(true);
        campaign.setResultPublishedBy(currentUser);
        campaign.setResultPublishedAt(LocalDateTime.now());
        campaign.setFinalDonorCount(donorCount);
        campaign.setFinalVolunteerCount(volunteerCount);
        campaignRepository.save(campaign);
        List<CampaignMedia> resultMedia = saveResultMedia(campaign, request.mediaIds());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    sendResultNotifications(campaign);
                } catch (Exception ex) {
                    log.error("Failed to send result notifications for campaignId={}: {}", campaignId, ex.getMessage(), ex);
                }
            }
        });

        log.info("Campaign result posted: campaignId={}, userId={}", campaignId, currentUser.getId());
        return buildResponse(campaign, confirmedTotal, resultMedia);
    }

    @Override
    @Transactional
    public CampaignResultResponse updateResult(Long campaignId, CampaignResultRequest request, User currentUser) {
        Campaign campaign = findAndValidateCampaign(campaignId);
        checkAuthorized(campaign, currentUser);

        if (!campaign.isResultPosted()) {
            throw new AppException(ErrorCode.CAMPAIGN_RESULT_NOT_FOUND);
        }

        long confirmedTotal = donationRepository.sumConfirmedAmountByCampaignId(campaignId);
        long finalAmount = request.finalAmountRaised() != null ? request.finalAmountRaised() : confirmedTotal;

        campaign.setResultSummary(request.resultSummary());
        campaign.setFinalAmountRaised(finalAmount);
        campaign.setItemsSummary(request.itemsSummary());
        campaign.setAcknowledgements(request.acknowledgements());
        campaign.setTaskSummary(request.taskSummary());
        campaign.setResultPublishedBy(currentUser);
        campaign.setResultPublishedAt(LocalDateTime.now());
        campaignRepository.save(campaign);
        List<CampaignMedia> resultMedia = saveResultMedia(campaign, request.mediaIds());

        log.info("Campaign result updated: campaignId={}, userId={}", campaignId, currentUser.getId());
        return buildResponse(campaign, confirmedTotal, resultMedia);
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignResultResponse getResult(Long campaignId) {
        log.info("Fetching campaign result: campaignId={}", campaignId);
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND));

        if (!campaign.isResultPosted()) {
            throw new AppException(ErrorCode.CAMPAIGN_RESULT_NOT_FOUND);
        }

        long confirmedTotal = donationRepository.sumConfirmedAmountByCampaignId(campaignId);
        return buildResponse(campaign, confirmedTotal);
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignResultGenerateResponse generateResultDraft(Long campaignId, User currentUser) {
        log.info("Generating AI result draft: campaignId={}, userId={}", campaignId, currentUser.getId());
        Campaign campaign = findAndValidateCampaign(campaignId);
        checkAuthorized(campaign, currentUser);

        long confirmedTotal = donationRepository.sumConfirmedAmountByCampaignId(campaignId);
        long donorCount = donationRepository.countDistinctDonorsByCampaignId(campaignId);
        long volunteerCount = campaignMemberRepository.countByCampaignIdAndRoleInCampaign(
                campaignId, CampaignMemberRole.VOLUNTEER);
        double goalPercent = calculateGoalPercent(campaign.getTarget(), confirmedTotal);

        List<Donation> donations = donationRepository.findByCampaignIdAndStatus(campaignId, DonationStatus.SUCCESSFUL);

        List<String> goodsDescriptions = donations.stream()
                .filter(d -> d.getType() == DonationType.GOODS)
                .map(CampaignResultServiceImpl::describeGoods)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        long moneyDonationCount = donations.stream().filter(d -> d.getType() == DonationType.MONEY).count();
        long goodsDonationCount = donations.stream().filter(d -> d.getType() == DonationType.GOODS).count();

        List<String> categories = campaign.getCategories().stream()
                .map(Category::getName)
                .filter(name -> name != null && !name.isBlank())
                .toList();

        Integer durationDays = campaign.getStartDate() != null && campaign.getEndDate() != null
                ? (int) ChronoUnit.DAYS.between(campaign.getStartDate().toLocalDate(), campaign.getEndDate().toLocalDate())
                : null;

        List<String> announcements = announcementRepository
                .findByCampaignIdOrderByPublishedAtAsc(campaignId)
                .stream()
                .map(CampaignResultServiceImpl::describeAnnouncement)
                .toList();

        List<CampaignTask> tasks = campaignTaskRepository.findActiveTasksWithAssignments(campaignId);
        long taskCount = tasks.size();
        long completedTaskCount = tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        List<String> taskDescriptions = tasks.stream()
                .map(CampaignResultServiceImpl::describeTask)
                .toList();

        return geminiService.generateCampaignResultDraft(campaign, new CampaignResultDraftContext(
                confirmedTotal, donorCount, volunteerCount, goalPercent,
                categories, durationDays, moneyDonationCount, goodsDonationCount,
                announcements, goodsDescriptions, buildBiggestDonorDescription(donations),
                taskCount, completedTaskCount, taskDescriptions));
    }

    /**
     * Re-tags campaign media as belonging to the final report (context=FINAL_REPORT) so it's
     * excluded from the general campaign gallery. Media dropped from the report reverts to
     * context=CAMPAIGN. Media already attached to an announcement or meeting (via FK) is
     * rejected rather than silently stolen. Returns the final FINAL_REPORT-tagged media in the
     * order the caller requested, so buildResponse can reuse it without re-querying.
     */
    private List<CampaignMedia> saveResultMedia(Campaign campaign, List<Long> mediaIds) {
        List<CampaignMedia> currentMedia = campaignMediaRepository
                .findByCampaignIdAndContextAndDeletedAtIsNull(campaign.getId(), MediaContext.FINAL_REPORT);

        return mediaService.reconcileMediaTags(campaign.getId(), currentMedia, mediaIds,
                media -> media.setContext(MediaContext.CAMPAIGN),
                (media, index) -> {
                    if (media.getAnnouncement() != null || media.getMeeting() != null) {
                        throw new AppException(ErrorCode.VALIDATION_ERROR,
                                "Media with ID " + media.getId() + " is already attached to another feature");
                    }
                    if (media.isCover()) {
                        throw new AppException(ErrorCode.VALIDATION_ERROR,
                                "Campaign cover photo cannot be attached to the final report");
                    }
                    media.setContext(MediaContext.FINAL_REPORT);
                });
    }

    /**
     * Intentionally uncapped: campaigns can be overfunded, and every reader of this value
     * (web report, PDF, AI prompt) must show the true percent rather than silently hiding
     * overfunding at 100%. Only visual elements with a fixed-width track (e.g. a progress bar)
     * should clamp for display — clamp there, not here.
     */
    private static double calculateGoalPercent(Long target, long amount) {
        return target != null && target > 0 ? (amount * 100.0) / target : 0.0;
    }

    private static String describeAnnouncement(Announcement announcement) {
        String date = announcement.getPublishedAt() != null
                ? announcement.getPublishedAt().format(ANNOUNCEMENT_DATE_FORMAT)
                : "date unknown";
        return String.format("%s (%s)", announcement.getTitle(), date);
    }

    private static String describeTask(CampaignTask task) {
        // Dedup by user id, not name — two different volunteers can share a display name,
        // and deduping on the string would silently drop one of them from the credit.
        String assignees = task.getAssignments().stream()
                .map(TaskAssignment::getUser)
                .filter(u -> u != null && u.getId() != null)
                .collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a, LinkedHashMap::new))
                .values()
                .stream()
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
        String statusLabel = switch (task.getStatus()) {
            case null -> "Unknown";
            case TODO -> "To Do";
            case IN_PROGRESS -> "In Progress";
            case DONE -> "Done";
        };
        String description = task.getDescription() != null && !task.getDescription().isBlank()
                ? truncate(task.getDescription(), MAX_TASK_DESCRIPTION_LENGTH)
                : "No description";
        // Uses " | " (not "; ") as the internal separator: joinForPrompt joins multiple task
        // facts with "; ", so reusing it here would make the boundary between one task's
        // assignee list and the next task's title ambiguous to the model. Free-text title/
        // description could itself contain "|" (or the field-label words below), which would
        // forge a fake field boundary, so both are stripped of the delimiter before assembly.
        return String.format("task title \"%s\" | description: %s | status: %s | assignee(s): %s",
                stripDelimiter(task.getTitle()), stripDelimiter(description), statusLabel,
                assignees.isBlank() ? "Unassigned" : assignees);
    }

    private static String stripDelimiter(String value) {
        return value == null ? "" : value.replace("|", "/");
    }

    private static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) + "…" : value;
    }

    /**
     * Honors the single non-anonymous donor who gave the most money. If that donor also
     * donated goods, those are included so they are credited for everything they gave.
     * Goods-only donors (no money) are never named here.
     *
     * @return a display string, or null if there is no eligible donor
     */
    private static String buildBiggestDonorDescription(List<Donation> donations) {
        record Contributor(String name, long moneyTotal, List<String> goodsItems) {}

        Map<Long, List<Donation>> byUser = donations.stream()
                .filter(d -> d.getUser() != null && !d.isAnonymous())
                .collect(Collectors.groupingBy(d -> d.getUser().getId(), LinkedHashMap::new, Collectors.toList()));

        return byUser.values().stream()
                .map(userDonations -> new Contributor(
                        userDonations.get(0).getUser().getFullName(),
                        userDonations.stream()
                                .filter(d -> d.getType() == DonationType.MONEY && d.getAmount() != null)
                                .mapToLong(Donation::getAmount)
                                .sum(),
                        userDonations.stream()
                                .filter(d -> d.getType() == DonationType.GOODS)
                                .map(CampaignResultServiceImpl::describeGoods)
                                .filter(s -> !s.isBlank())
                                .distinct()
                                .toList()))
                .filter(c -> c.moneyTotal() > 0)
                .max(Comparator.comparingLong(Contributor::moneyTotal))
                .map(c -> c.goodsItems().isEmpty()
                        ? String.format("%s (%,d VND)", c.name(), c.moneyTotal())
                        : String.format("%s (%,d VND; goods: %s)", c.name(), c.moneyTotal(), String.join(", ", c.goodsItems())))
                .orElse(null);
    }

    private void sendResultNotifications(Campaign campaign) {
        String campaignName = campaign.getTitle() != null ? campaign.getTitle() : "this campaign";
        String linkUrl = "/campaigns/" + campaign.getId() + "/result";
        String reportLink = buildReportLink(campaign.getId());
        byte[] reportPdf = tryRenderResultPdf(campaign);
        String reportPdfFilename = "final-report-" + campaign.getId() + ".pdf";

        // --- Followers (single query) ---
        List<User> followerUsers = campaignFollowerRepository.findFollowerUsersByCampaignId(campaign.getId());
        if (!followerUsers.isEmpty()) {
            Set<NotificationRecipient> followerRecipients = followerUsers.stream()
                    .map(u -> new NotificationRecipient(u.getId(), u.getEmail()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            notificationService.createNotification(CreateNotificationCommand.builder()
                    .recipients(followerRecipients)
                    .type(NotificationType.CAMPAIGN_RESULT_POSTED)
                    .title("Campaign Results Published")
                    .message("The final results for \"" + campaignName + "\" have been posted. "
                            + "Thank you for following and supporting this campaign — your interest truly mattered to us!")
                    .linkUrl(linkUrl)
                    .build());
            for (User user : followerUsers) {
                emailService.sendHtmlEmail(
                        user.getEmail(),
                        RESULT_EMAIL_SUBJECT_PREFIX + campaignName,
                        buildFollowerEmailBody(user.getFullName(), campaignName));
            }
        }

        // --- Volunteers (single query) ---
        List<User> volunteerUsers =
                campaignMemberRepository.findUsersByCampaignIdAndRole(campaign.getId(), CampaignMemberRole.VOLUNTEER);
        if (!volunteerUsers.isEmpty()) {
            Set<NotificationRecipient> volunteerRecipients = volunteerUsers.stream()
                    .map(u -> new NotificationRecipient(u.getId(), u.getEmail()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            notificationService.createNotification(CreateNotificationCommand.builder()
                    .recipients(volunteerRecipients)
                    .type(NotificationType.CAMPAIGN_RESULT_POSTED)
                    .title("Campaign Results Published")
                    .message("The final results for \"" + campaignName + "\" are now available. "
                            + "Thank you for your dedication and hard work — your contribution made a real difference!")
                    .linkUrl(linkUrl)
                    .build());
            for (User user : volunteerUsers) {
                String subject = RESULT_EMAIL_SUBJECT_PREFIX + campaignName;
                String body = buildVolunteerEmailBody(user.getFullName(), campaignName, reportLink);
                if (reportPdf != null) {
                    emailService.sendHtmlEmailWithAttachment(
                            user.getEmail(), subject, body, reportPdf, reportPdfFilename, "application/pdf");
                } else {
                    emailService.sendHtmlEmail(user.getEmail(), subject, body);
                }
            }
        }

        // --- Donors (personalized with donation amount) ---
        List<DonorNotificationInfo> donors =
                donationRepository.findDonorNotificationInfoByCampaignId(campaign.getId(), DonationStatus.SUCCESSFUL);
        Map<Long, String> aiThankYouMessages = generateDonorThankYouMessages(campaign);
        for (DonorNotificationInfo donor : donors) {
            // totalAmount is null for donors who only gave goods (GOODS donations carry no amount)
            String formattedAmount = donor.totalAmount() != null && donor.totalAmount() > 0
                    ? NumberFormat.getNumberInstance(Locale.US).format(donor.totalAmount())
                    : null;
            String donationThanks = formattedAmount != null
                    ? "Thank you for your generous donation of " + formattedAmount + " VND — together, we made it happen!"
                    : "Thank you for your generous contribution — together, we made it happen!";
            notificationService.createNotification(CreateNotificationCommand.builder()
                    .recipients(Set.of(new NotificationRecipient(donor.userId(), donor.email())))
                    .type(NotificationType.CAMPAIGN_RESULT_POSTED)
                    .title("Campaign Results Published")
                    .message("The final results for \"" + campaignName + "\" are now available. " + donationThanks)
                    .linkUrl(linkUrl)
                    .build());

            String subject = RESULT_EMAIL_SUBJECT_PREFIX + campaignName;
            String body = buildDonorEmailBody(donor.fullName(), campaignName, formattedAmount,
                    aiThankYouMessages.get(donor.userId()), reportLink);
            if (reportPdf != null) {
                emailService.sendHtmlEmailWithAttachment(
                        donor.email(), subject, body, reportPdf, reportPdfFilename, "application/pdf");
            } else {
                emailService.sendHtmlEmail(donor.email(), subject, body);
            }
        }
    }

    /**
     * Builds per-donor contribution data from the database and asks the AI for personalized
     * thank-you messages. Returns an empty map on any failure so emails fall back to the
     * static template.
     */
    private Map<Long, String> generateDonorThankYouMessages(Campaign campaign) {
        if (!geminiService.isConfigured()) {
            return Map.of();
        }
        try {
            List<Donation> donations =
                    donationRepository.findByCampaignIdAndStatus(campaign.getId(), DonationStatus.SUCCESSFUL);
            Map<Long, List<Donation>> byUser = donations.stream()
                    .filter(d -> d.getUser() != null)
                    .collect(Collectors.groupingBy(d -> d.getUser().getId(), LinkedHashMap::new, Collectors.toList()));

            List<DonorThankYouContext> contexts = new ArrayList<>();
            for (Map.Entry<Long, List<Donation>> entry : byUser.entrySet()) {
                List<Donation> userDonations = entry.getValue();
                long totalMoney = userDonations.stream()
                        .filter(d -> d.getType() == DonationType.MONEY && d.getAmount() != null)
                        .mapToLong(Donation::getAmount)
                        .sum();
                List<String> goodsItems = userDonations.stream()
                        .filter(d -> d.getType() == DonationType.GOODS)
                        .map(CampaignResultServiceImpl::describeGoods)
                        .filter(s -> !s.isBlank())
                        .toList();
                contexts.add(new DonorThankYouContext(
                        entry.getKey(),
                        userDonations.get(0).getUser().getFullName(),
                        totalMoney,
                        userDonations.size(),
                        goodsItems));
            }

            return geminiService.generateDonorThankYouMessages(campaign, contexts);
        } catch (Exception e) {
            log.warn("Falling back to static donor emails, AI generation failed: campaignId={}, error={}",
                    campaign.getId(), e.getMessage());
            return Map.of();
        }
    }

    private static String describeGoods(Donation donation) {
        StringBuilder sb = new StringBuilder();
        if (donation.getGoodsCategory() != null && !donation.getGoodsCategory().isBlank()) {
            sb.append(donation.getGoodsCategory());
        }
        if (donation.getDetail() != null && !donation.getDetail().isBlank()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(donation.getDetail());
        }
        return sb.toString();
    }

    private String buildFollowerEmailBody(String fullName, String campaignName) {
        Context context = new Context();
        context.setVariable("fullName", fullName);
        context.setVariable("campaignName", campaignName);
        return templateEngine.process("follower-result-notification", context);
    }

    private String buildVolunteerEmailBody(String fullName, String campaignName, String reportLink) {
        Context context = new Context();
        context.setVariable("fullName", fullName);
        context.setVariable("campaignName", campaignName);
        context.setVariable("reportLink", reportLink);
        return templateEngine.process("volunteer-result-notification", context);
    }

    private String buildDonorEmailBody(String fullName, String campaignName, String formattedAmount, String aiMessage,
            String reportLink) {
        Context context = new Context();

        context.setVariable("fullName", fullName);
        context.setVariable("campaignName", campaignName);
        context.setVariable("formattedAmount", formattedAmount);
        context.setVariable("aiMessage", aiMessage);
        context.setVariable("reportLink", reportLink);

        return templateEngine.process(
                "donor-result-notification",
                context);
    }

    private Campaign findAndValidateCampaign(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND));
        if (campaign.getStatus() != CampaignStatus.COMPLETED) {
            log.warn("Campaign {} is not completed, status={}", campaignId, campaign.getStatus());
            throw new AppException(ErrorCode.CAMPAIGN_NOT_COMPLETED);
        }
        return campaign;
    }

    private void checkAuthorized(Campaign campaign, User currentUser) {
        if (!campaignMemberService.canManageCampaign(campaign.getId(), currentUser)) {
            log.warn("Unauthorized result access: campaignId={}, userId={}", campaign.getId(), currentUser.getId());
            throw new AppException(ErrorCode.UNAUTHORIZED_RESULT_ACCESS);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateResultPdf(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND));
        if (!campaign.isResultPosted()) {
            throw new AppException(ErrorCode.CAMPAIGN_RESULT_NOT_FOUND);
        }
        return renderResultPdf(campaign);
    }

    /**
     * Same as {@link #renderResultPdf} but swallows rendering failures, returning null instead
     * of throwing - used when sending result notification emails so a PDF rendering bug never
     * blocks the emails themselves from going out.
     */
    private byte[] tryRenderResultPdf(Campaign campaign) {
        try {
            return renderResultPdf(campaign);
        } catch (Exception e) {
            log.error("Failed to render final report PDF for notification email: campaignId={}", campaign.getId(), e);
            return null;
        }
    }

    private byte[] renderResultPdf(Campaign campaign) {
        long totalRaised = campaign.getFinalAmountRaised() != null ? campaign.getFinalAmountRaised() : 0L;
        double goalPercent = calculateGoalPercent(campaign.getTarget(), totalRaised);
        TaskCounts taskCounts = computeActiveTaskCounts(campaign.getId());

        Context context = new Context();
        context.setVariable("campaignName", campaign.getTitle());
        context.setVariable("resultSummary", campaign.getResultSummary());
        context.setVariable("itemsSummary", campaign.getItemsSummary());
        context.setVariable("acknowledgements", campaign.getAcknowledgements());
        context.setVariable("taskSummary", campaign.getTaskSummary());
        context.setVariable("taskCount", taskCounts.total());
        context.setVariable("completedTaskCount", taskCounts.completed());
        context.setVariable("publishedByName", campaign.getResultPublishedBy() != null
                ? campaign.getResultPublishedBy().getFullName() : null);
        context.setVariable("publishedAt", campaign.getResultPublishedAt() != null
                ? campaign.getResultPublishedAt().format(ANNOUNCEMENT_DATE_FORMAT) : null);
        context.setVariable("totalRaised", NumberFormat.getNumberInstance(Locale.US).format(totalRaised));
        context.setVariable("donorCount", campaign.getFinalDonorCount() != null ? campaign.getFinalDonorCount() : 0L);
        context.setVariable("volunteerCount",
                campaign.getFinalVolunteerCount() != null ? campaign.getFinalVolunteerCount() : 0L);
        context.setVariable("goalPercent", String.format(Locale.US, "%.0f", goalPercent));
        context.setVariable("reportLink", buildReportLink(campaign.getId()));
        context.setVariable("galleryMedia", buildGalleryMedia(campaign));

        String html = templateEngine.process("final-report-pdf", context);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // The base PDF fonts have no Vietnamese glyph coverage, so campaign/report text
            // with diacritics would render as tofu boxes without an embedded Unicode font.
            builder.useFont(() -> getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf"),
                    PDF_FONT_FAMILY, 400, BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(() -> getClass().getResourceAsStream("/fonts/NotoSans-Bold.ttf"),
                    PDF_FONT_FAMILY, 700, BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("Failed to render final report PDF: campaignId={}", campaign.getId(), e);
            throw new AppException(ErrorCode.UNCATEGORIZED_ERROR, "Failed to generate final report PDF");
        }
    }

    private String buildReportLink(Long campaignId) {
        return UriComponentsBuilder
                .fromUriString(mailProps.getFrontendUrl())
                .pathSegment("campaigns", campaignId.toString(), "result")
                .toUriString();
    }

    /**
     * Same non-cover media the public final report page shows (campaign.medias, minus the
     * cover image). Images are embedded as downscaled JPEG data URIs rather than the original
     * files - embedding full-resolution originals ballooned the PDF to several MB, which in
     * turn made the emailed copy large enough that Gmail clips the message body. Videos can't
     * play in a PDF, so they render as a placeholder card instead.
     */
    private List<PdfMediaItem> buildGalleryMedia(Campaign campaign) {
        return campaignMediaRepository.findByCampaignIdAndDeletedAtIsNull(campaign.getId()).stream()
                .filter(m -> !m.isCover())
                .map(m -> new PdfMediaItem(
                        "VIDEO".equalsIgnoreCase(m.getMediaType()) ? null : buildImageDataUri(m.getUrl()),
                        "VIDEO".equalsIgnoreCase(m.getMediaType())))
                .filter(item -> item.isVideo() || item.url() != null)
                .toList();
    }

    private String buildImageDataUri(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        File file = Paths.get(uploadDir).resolve(filename).toFile();
        if (!file.exists()) {
            return null;
        }
        try {
            BufferedImage original = ImageIO.read(file);
            if (original == null) {
                return null;
            }
            BufferedImage resized = resizeToMaxDimension(original, PDF_IMAGE_MAX_DIMENSION);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            writeJpeg(resized, buffer, PDF_IMAGE_JPEG_QUALITY);
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(buffer.toByteArray());
        } catch (IOException e) {
            log.warn("Failed to load gallery image for final report PDF: file={}", filename, e);
            return null;
        }
    }

    private static BufferedImage resizeToMaxDimension(BufferedImage original, int maxDimension) {
        int width = original.getWidth();
        int height = original.getHeight();
        if (Math.max(width, height) <= maxDimension) {
            return toOpaqueRgb(original);
        }
        double scale = (double) maxDimension / Math.max(width, height);
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));
        Image scaledImage = original.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(scaledImage, 0, 0, Color.WHITE, null);
        g.dispose();
        return resized;
    }

    // JPEG has no alpha channel - flatten onto a white background so transparent PNGs don't
    // come out with garbled colors.
    private static BufferedImage toOpaqueRgb(BufferedImage original) {
        if (original.getType() == BufferedImage.TYPE_INT_RGB) {
            return original;
        }
        BufferedImage rgb = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(original, 0, 0, Color.WHITE, null);
        g.dispose();
        return rgb;
    }

    private static void writeJpeg(BufferedImage image, ByteArrayOutputStream output, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private record PdfMediaItem(String url, boolean isVideo) {}

    private CampaignResultResponse buildResponse(Campaign campaign, long confirmedTotal) {
        List<CampaignMedia> resultMedia = campaignMediaRepository
                .findByCampaignIdAndContextAndDeletedAtIsNull(campaign.getId(), MediaContext.FINAL_REPORT);
        return buildResponse(campaign, confirmedTotal, resultMedia);
    }

    private CampaignResultResponse buildResponse(Campaign campaign, long confirmedTotal, List<CampaignMedia> resultMedia) {
        long donorCount = campaign.getFinalDonorCount() != null
                ? campaign.getFinalDonorCount()
                : donationRepository.countDistinctDonorsByCampaignId(campaign.getId());
        long volunteerCount = campaign.getFinalVolunteerCount() != null
                ? campaign.getFinalVolunteerCount()
                : campaignMemberRepository.countByCampaignIdAndRoleInCampaign(campaign.getId(), CampaignMemberRole.VOLUNTEER);

        long amountForGoal = campaign.getFinalAmountRaised() != null ? campaign.getFinalAmountRaised() : confirmedTotal;
        double goalPercent = calculateGoalPercent(campaign.getTarget(), amountForGoal);

        TaskCounts taskCounts = computeActiveTaskCounts(campaign.getId());

        List<CampaignMediaResponse> mediaResponses = campaignMediaMapper.toResponseList(resultMedia);

        return CampaignResultResponse.builder()
                .campaignId(campaign.getId())
                .resultSummary(campaign.getResultSummary())
                .finalAmountRaised(campaign.getFinalAmountRaised())
                .itemsSummary(campaign.getItemsSummary())
                .acknowledgements(campaign.getAcknowledgements())
                .taskSummary(campaign.getTaskSummary())
                .publishedByName(campaign.getResultPublishedBy() != null
                        ? campaign.getResultPublishedBy().getFullName() : null)
                .publishedAt(campaign.getResultPublishedAt())
                .media(mediaResponses)
                .totalRaised(confirmedTotal)
                .donorCount(donorCount)
                .volunteerCount(volunteerCount)
                .goalPercent(goalPercent)
                .taskCount(taskCounts.total())
                .completedTaskCount(taskCounts.completed())
                .build();
    }

    private record TaskCounts(long total, long completed) {}

    private TaskCounts computeActiveTaskCounts(Long campaignId) {
        Specification<CampaignTask> activeTaskSpec = Specification
                .where(CampaignTaskSpecifications.hasCampaignId(campaignId))
                .and(CampaignTaskSpecifications.isNotDeleted())
                .and(CampaignTaskSpecifications.hasIsArchived(false));
        long total = campaignTaskRepository.count(activeTaskSpec);
        long completed = campaignTaskRepository
                .count(activeTaskSpec.and(CampaignTaskSpecifications.hasStatus(TaskStatus.DONE)));
        return new TaskCounts(total, completed);
    }
}
