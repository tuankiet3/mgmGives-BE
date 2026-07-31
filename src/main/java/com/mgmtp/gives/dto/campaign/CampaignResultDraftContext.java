package com.mgmtp.gives.dto.campaign;

import java.util.List;

/**
 * Aggregated campaign statistics and real events handed to the AI when generating a result
 * draft. Every field here must be a verifiable fact — the prompt built from this context
 * instructs the model to use ONLY these facts and never invent details.
 *
 * @param categories        campaign category names, e.g. "Education", "Disaster Relief"
 * @param durationDays      number of days between start and end date
 * @param moneyDonationCount number of individual money donations (distinct from donorCount)
 * @param goodsDonationCount number of individual goods donations
 * @param announcements     published announcement titles with their date, oldest first,
 *                          e.g. "Distribution day at Hoa Vang school (Jun 12, 2026)"
 * @param goodsDescriptions campaign-wide deduplicated goods descriptions
 * @param biggestDonor      the single non-anonymous donor who gave the most money, including
 *                          any goods they also donated, e.g. "Jane Doe (2,500,000 VND; goods:
 *                          winter jackets - 10 pcs)" — null if no donor gave any money
 * @param taskCount         total non-archived tasks tracked for this campaign
 * @param completedTaskCount tasks with status DONE among taskCount
 * @param taskDescriptions  per-task summary: title, admin-authored description, status, and
 *                          assignee names. The description is free text entered by the campaign
 *                          admin/assignees when creating the task — same trust level as the
 *                          campaign description and goods descriptions elsewhere in this record,
 *                          not scrubbed of names or other detail the author chose to include.
 * @param spendingDescriptions per-entry fund usage: amount, admin-authored description, and
 *                          date spent, e.g. "500,000 VND - Purchased 20 blankets for the
 *                          shelter (Jun 5, 2026)". Same trust level as taskDescriptions.
 */
public record CampaignResultDraftContext(
        long totalRaised,
        long donorCount,
        long volunteerCount,
        double goalPercent,
        List<String> categories,
        Integer durationDays,
        long moneyDonationCount,
        long goodsDonationCount,
        List<String> announcements,
        List<String> goodsDescriptions,
        String biggestDonor,
        long taskCount,
        long completedTaskCount,
        List<String> taskDescriptions,
        List<String> spendingDescriptions) {
}
