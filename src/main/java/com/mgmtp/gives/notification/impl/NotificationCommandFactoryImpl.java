package com.mgmtp.gives.notification.impl;

import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.enums.NotificationType;
import com.mgmtp.gives.event.notification.*;
import com.mgmtp.gives.notification.NotificationCommandFactory;
import com.mgmtp.gives.notification.NotificationRecipientResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component @RequiredArgsConstructor @Slf4j
public class NotificationCommandFactoryImpl implements NotificationCommandFactory {
    private final NotificationRecipientResolver recipientResolver;

    public CreateNotificationCommand donationConfirmed(DonationConfirmedEvent event) {
        String contributionText = formatContribution(
                event.donationType(),
                event.amount()
        );

        return CreateNotificationCommand.builder()
                .recipients(recipientResolver.singleUser(event.donorUserId()))
                .type(NotificationType.DONATION_CONFIRMED)
                .title("Your kindness just got confirmed ✨")
                .message(
                        "Your donation of " + contributionText +
                                " to campaign \"" + event.campaignTitle() +
                                "\" has been confirmed. Kindness looks good on you 😄"
                )
                .linkUrl("/my-donations")
                .build();
    }

    public CreateNotificationCommand campaignDonationConfirmed(CampaignDonationConfirmedEvent event) {
        Set<NotificationRecipient> recipients =
                recipientResolver.campaignOwnerAndFollowersExceptDonor(
                        event.campaignId(),
                        event.donorUserId()
                );

        if (event.confirmedById() != null) {
            recipients.removeIf(r -> r.userId().equals(event.confirmedById()));
        }

        recipients.forEach(recipient -> log.info("Recipient: {}", recipient.userId()));

        log.info(
                "Campaign new donation notification command created: donationId={}, campaignId={}, recipientCount={}",
                event.donationId(),
                event.campaignId(),
                recipients.size()
        );
        String donorDisplayName = event.anonymous() ? "*****" : event.donorName();

        String contributionText = formatContribution(
                event.donationType(),
                event.amount()
        );

        return CreateNotificationCommand.builder()
                .recipients(recipients)
                .type(NotificationType.CAMPAIGN_DONATION_CONFIRMED)
                .title("New donation confirmed 🎉")
                .message(
                        donorDisplayName +
                                " donated " + contributionText +
                                " to campaign \"" + event.campaignTitle() + "\"."
                )
                .linkUrl("/campaigns/" + event.campaignId())
                .build();
    }

    public CreateNotificationCommand campaignStatusChanged(CampaignStatusChangedEvent event) {
        Set<NotificationRecipient> recipients = event.oldStatus() == CampaignStatus.PENDING
                ? recipientResolver.campaignOwner(event.campaignId())
                : recipientResolver.campaignOwnerAndFollowers(event.campaignId());

        return CreateNotificationCommand.builder()
                .recipients(recipients)
                .type(NotificationType.CAMPAIGN_STATUS_CHANGED)
                .title(buildCampaignStatusChangedTitle(event.newStatus()))
                .message(
                        "Campaign \"" + event.campaignTitle() +
                                "\" status changed from " + formatCampaignStatus(event.oldStatus()) +
                                " to " + formatCampaignStatus(event.newStatus()) + "."
                )
                .linkUrl("/campaigns/" + event.campaignId())
                .build();
    }

    public CreateNotificationCommand campaignUnjoinRequested(CampaignUnjoinRequestedEvent event) {
        return CreateNotificationCommand.builder()
                .recipients(recipientResolver.campaignAdmins(event.campaignId()))
                .type(NotificationType.CAMPAIGN_UNJOIN_REQUESTED)
                .title("Unjoin request awaiting approval")
                .message(
                        event.requesterName() + " wants to unjoin campaign \"" + event.campaignTitle() +
                                "\" but still has an assigned task. Review the request to approve or reject it."
                )
                .linkUrl("/campaigns/" + event.campaignId() + "/unjoin-requests")
                .build();
    }

    public CreateNotificationCommand campaignUnjoinApproved(CampaignUnjoinApprovedEvent event) {
        return CreateNotificationCommand.builder()
                .recipients(recipientResolver.singleUser(event.userId()))
                .type(NotificationType.CAMPAIGN_UNJOIN_APPROVED)
                .title("Unjoin request approved")
                .message(
                        "Your request to unjoin campaign \"" + event.campaignTitle() +
                                "\" has been approved. You are no longer a member of this campaign."
                )
                .linkUrl("/campaigns/" + event.campaignId())
                .build();
    }

    public CreateNotificationCommand campaignUnjoinRejected(CampaignUnjoinRejectedEvent event) {
        return CreateNotificationCommand.builder()
                .recipients(recipientResolver.singleUser(event.userId()))
                .type(NotificationType.CAMPAIGN_UNJOIN_REJECTED)
                .title("Unjoin request rejected")
                .message(
                        "Your request to unjoin campaign \"" + event.campaignTitle() +
                                "\" was rejected: " + event.reason()
                )
                .linkUrl("/campaigns/" + event.campaignId())
                .build();
    }

    private String buildCampaignStatusChangedTitle(CampaignStatus newStatus) {
        return switch (newStatus) {
            case APPROVED -> "Campaign approved";
            case REJECTED -> "Campaign rejected";
            case IN_PROGRESS -> "Campaign started";
            case COMPLETED -> "Campaign completed";
            default -> "Campaign status updated";
        };
    }

    private String formatCampaignStatus(CampaignStatus status) {
        if (status == null) {
            return "Unknown";
        }

        return switch (status) {
            case DRAFT -> "Draft";
            case PENDING -> "Pending";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
            case IN_PROGRESS -> "In progress";
            case COMPLETED -> "Completed";
        };
    }

    private String formatContribution(DonationType donationType, Long amount) {
        if (donationType == DonationType.MONEY) return formatVnd(amount);
        return "some goods";
    }

    private String formatVnd(Long amount) {
        if (amount == null) {
            return "0 VND";
        }

        return String.format("%,d VND", amount);
    }

    @Override
    public CreateNotificationCommand taskAssigned(TaskAssignedEvent event) {
        return CreateNotificationCommand.builder()
                .recipients(event.assignees())
                .type(NotificationType.TASK_ASSIGNED)
                .title("New task assigned")
                .message("You have been assigned to task \"" + event.taskTitle() + "\".")
                .linkUrl("/campaigns/" + event.campaignId() + "/tasks")
                .build();
    }

    @Override
    public CreateNotificationCommand taskDescriptionUpdated(TaskDescriptionUpdatedEvent event) {
        return CreateNotificationCommand.builder()
                .recipients(event.recipients())
                .type(NotificationType.TASK_ASSIGNED)
                .title("Task description updated")
                .message("The description of task \"" + event.taskTitle() + "\" has been updated.")
                .linkUrl("/campaigns/" + event.campaignId() + "/tasks")
                .build();
    }

    @Override
    public CreateNotificationCommand taskUnassigned(TaskUnassignedEvent event) {
        return CreateNotificationCommand.builder()
                .recipients(java.util.Set.of(event.recipient()))
                .type(NotificationType.TASK_ASSIGNED)
                .title("Task assignment removed")
                .message("You have been unassigned from task \"" + event.taskTitle() + "\".")
                .linkUrl("/campaigns/" + event.campaignId() + "/tasks")
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

    @Override
    public CreateNotificationCommand taskStatusChanged(TaskStatusChangedEvent event) {
        return CreateNotificationCommand.builder()
                .recipients(event.recipients())
                .type(NotificationType.TASK_ASSIGNED)
                .title("Task status updated")
                .message("Task \"" + event.taskTitle() + "\" status has been changed from " +
                        formatTaskStatus(event.oldStatus()) + " to " + formatTaskStatus(event.newStatus()) + ".")
                .linkUrl("/campaigns/" + event.campaignId() + "/tasks")
                .build();
    }

    @Override
    public List<CreateNotificationCommand> announcementReplyCreated(AnnouncementReplyCreatedEvent event) {
        List<CreateNotificationCommand> commands = new ArrayList<>(2);
        Long directRecipientId = event.referencedReplyAuthorId();

        if (isEligibleRecipient(directRecipientId, event.replyAuthorId())) {
            commands.add(CreateNotificationCommand.builder()
                    .recipients(recipientResolver.singleUser(directRecipientId))
                    .type(NotificationType.ANNOUNCEMENT_REPLY)
                    .title("New reply to your comment")
                    .message(event.replyAuthorName() + " replied to your comment on \"" + event.announcementTitle() + "\".")
                    .linkUrl(replyLink(event))
                    .build());
        }

        if (isEligibleRecipient(event.announcementPublisherId(), event.replyAuthorId())
                && !Objects.equals(event.announcementPublisherId(), directRecipientId)) {
            commands.add(CreateNotificationCommand.builder()
                    .recipients(recipientResolver.singleUser(event.announcementPublisherId()))
                    .type(NotificationType.ANNOUNCEMENT_REPLY)
                    .title("New reply on \"" + event.announcementTitle() + "\"")
                    .message(event.replyAuthorName() + " replied to your announcement.")
                    .linkUrl(replyLink(event))
                    .build());
        }

        return commands;
    }

    private static boolean isEligibleRecipient(Long recipientId, Long replyAuthorId) {
        return recipientId != null && !Objects.equals(recipientId, replyAuthorId);
    }

    private static String replyLink(AnnouncementReplyCreatedEvent event) {
        return "/campaigns/" + event.campaignId()
                + "/announcements/" + event.announcementId()
                + "?reply=" + event.announcementId() + ":" + event.replyId();
    }

    private String formatTaskStatus(com.mgmtp.gives.enums.TaskStatus status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (status) {
            case TODO -> "To do";
            case IN_PROGRESS -> "In progress";
            case DONE -> "Done";
        };
    }
}
