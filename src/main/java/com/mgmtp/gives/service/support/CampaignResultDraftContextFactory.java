package com.mgmtp.gives.service.support;

import com.mgmtp.gives.dto.campaign.CampaignResultDraftContext;
import com.mgmtp.gives.entity.Announcement;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignTask;
import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.TaskAssignment;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.enums.TaskStatus;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CampaignResultDraftContextFactory {

    private static final DateTimeFormatter ANNOUNCEMENT_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final int MAX_TASK_DESCRIPTION_LENGTH = 250;

    public CampaignResultDraftContext create(
            Campaign campaign,
            long confirmedTotal,
            long donorCount,
            long volunteerCount,
            List<Donation> donations,
            List<Announcement> announcements,
            List<CampaignTask> tasks) {
        List<String> goodsDescriptions = donations.stream()
                .filter(donation -> donation.getType() == DonationType.GOODS)
                .map(CampaignResultDraftContextFactory::describeGoods)
                .filter(description -> !description.isBlank())
                .distinct()
                .toList();

        long moneyDonationCount = donations.stream()
                .filter(donation -> donation.getType() == DonationType.MONEY)
                .count();
        long goodsDonationCount = donations.stream()
                .filter(donation -> donation.getType() == DonationType.GOODS)
                .count();

        List<String> categories = campaign.getCategories().stream()
                .map(Category::getName)
                .filter(name -> name != null && !name.isBlank())
                .toList();

        Integer durationDays = campaign.getStartDate() != null && campaign.getEndDate() != null
                ? (int) ChronoUnit.DAYS.between(
                        campaign.getStartDate().toLocalDate(), campaign.getEndDate().toLocalDate())
                : null;

        List<String> announcementDescriptions = announcements.stream()
                .map(CampaignResultDraftContextFactory::describeAnnouncement)
                .toList();
        long completedTaskCount = tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.DONE)
                .count();
        List<String> taskDescriptions = tasks.stream()
                .map(CampaignResultDraftContextFactory::describeTask)
                .toList();

        return new CampaignResultDraftContext(
                confirmedTotal,
                donorCount,
                volunteerCount,
                CampaignResultMetrics.calculateGoalPercent(campaign.getTarget(), confirmedTotal),
                categories,
                durationDays,
                moneyDonationCount,
                goodsDonationCount,
                announcementDescriptions,
                goodsDescriptions,
                buildBiggestDonorDescription(donations),
                tasks.size(),
                completedTaskCount,
                taskDescriptions);
    }

    public static String describeGoods(Donation donation) {
        StringBuilder description = new StringBuilder();
        if (donation.getGoodsCategory() != null && !donation.getGoodsCategory().isBlank()) {
            description.append(donation.getGoodsCategory());
        }
        if (donation.getDetail() != null && !donation.getDetail().isBlank()) {
            if (!description.isEmpty()) {
                description.append(" - ");
            }
            description.append(donation.getDetail());
        }
        return description.toString();
    }

    private static String describeAnnouncement(Announcement announcement) {
        String date = announcement.getPublishedAt() != null
                ? announcement.getPublishedAt().format(ANNOUNCEMENT_DATE_FORMAT)
                : "date unknown";
        return String.format("%s (%s)", announcement.getTitle(), date);
    }

    private static String describeTask(CampaignTask task) {
        String assignees = task.getAssignments().stream()
                .map(TaskAssignment::getUser)
                .filter(user -> user != null && user.getId() != null)
                .collect(Collectors.toMap(
                        User::getId,
                        User::getFullName,
                        (first, ignored) -> first,
                        LinkedHashMap::new))
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
        return String.format(
                "task title \"%s\" | description: %s | status: %s | assignee(s): %s",
                stripDelimiter(task.getTitle()),
                stripDelimiter(description),
                statusLabel,
                assignees.isBlank() ? "Unassigned" : assignees);
    }

    private static String stripDelimiter(String value) {
        return value == null ? "" : value.replace("|", "/");
    }

    private static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) + "…" : value;
    }

    private static String buildBiggestDonorDescription(List<Donation> donations) {
        record Contributor(String name, long moneyTotal, List<String> goodsItems) {
        }

        Map<Long, List<Donation>> byUser = donations.stream()
                .filter(donation -> donation.getUser() != null && !donation.isAnonymous())
                .collect(Collectors.groupingBy(
                        donation -> donation.getUser().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        return byUser.values().stream()
                .map(userDonations -> new Contributor(
                        userDonations.getFirst().getUser().getFullName(),
                        userDonations.stream()
                                .filter(donation -> donation.getType() == DonationType.MONEY
                                        && donation.getAmount() != null)
                                .mapToLong(Donation::getAmount)
                                .sum(),
                        userDonations.stream()
                                .filter(donation -> donation.getType() == DonationType.GOODS)
                                .map(CampaignResultDraftContextFactory::describeGoods)
                                .filter(description -> !description.isBlank())
                                .distinct()
                                .toList()))
                .filter(contributor -> contributor.moneyTotal() > 0)
                .max(Comparator.comparingLong(Contributor::moneyTotal))
                .map(contributor -> contributor.goodsItems().isEmpty()
                        ? String.format("%s (%,d VND)", contributor.name(), contributor.moneyTotal())
                        : String.format(
                                "%s (%,d VND; goods: %s)",
                                contributor.name(),
                                contributor.moneyTotal(),
                                String.join(", ", contributor.goodsItems())))
                .orElse(null);
    }
}
