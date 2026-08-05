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
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignResultDraftContextFactoryTest {

    private final CampaignResultDraftContextFactory factory = new CampaignResultDraftContextFactory();

    @Test
    void createsVerifiableCampaignFactsForTheAiDraft() {
        User primaryDonor = User.builder().id(7L).fullName("Mai Nguyen").build();
        User anonymousDonor = User.builder().id(8L).fullName("Hidden Donor").build();
        Campaign campaign = Campaign.builder()
                .target(1_000_000L)
                .startDate(LocalDateTime.of(2026, 6, 1, 9, 0))
                .endDate(LocalDateTime.of(2026, 6, 11, 18, 0))
                .categories(Set.of(
                        Category.builder().name("Education").build(),
                        Category.builder().name(" ").build()))
                .build();
        List<Donation> donations = List.of(
                Donation.builder()
                        .user(primaryDonor)
                        .type(DonationType.MONEY)
                        .amount(700_000L)
                        .build(),
                Donation.builder()
                        .user(primaryDonor)
                        .type(DonationType.GOODS)
                        .goodsCategory("Books")
                        .detail("20 boxes")
                        .build(),
                Donation.builder()
                        .user(primaryDonor)
                        .type(DonationType.GOODS)
                        .goodsCategory("Books")
                        .detail("20 boxes")
                        .build(),
                Donation.builder()
                        .user(anonymousDonor)
                        .type(DonationType.MONEY)
                        .amount(2_000_000L)
                        .isAnonymous(true)
                        .build());
        List<Announcement> announcements = List.of(Announcement.builder()
                .title("Distribution day")
                .publishedAt(LocalDateTime.of(2026, 6, 8, 10, 30))
                .build());

        CampaignResultDraftContext context = factory.create(
                campaign, 1_250_000L, 3L, 5L, donations, announcements, List.of());

        assertThat(context.totalRaised()).isEqualTo(1_250_000L);
        assertThat(context.goalPercent()).isEqualTo(125.0);
        assertThat(context.categories()).containsExactly("Education");
        assertThat(context.durationDays()).isEqualTo(10);
        assertThat(context.moneyDonationCount()).isEqualTo(2L);
        assertThat(context.goodsDonationCount()).isEqualTo(2L);
        assertThat(context.announcements()).containsExactly("Distribution day (Jun 8, 2026)");
        assertThat(context.goodsDescriptions()).containsExactly("Books - 20 boxes");
        assertThat(context.biggestDonor()).isEqualTo("Mai Nguyen (700,000 VND; goods: Books - 20 boxes)");
    }

    @Test
    void keepsTaskFactsBoundedAndDeduplicatesAssigneesByUserId() {
        User volunteer = User.builder().id(42L).fullName("An Tran").build();
        CampaignTask task = CampaignTask.builder()
                .title("Pack | deliver")
                .description("x".repeat(260) + "|unsafe delimiter")
                .status(TaskStatus.DONE)
                .assignments(Set.of(
                        TaskAssignment.builder().user(volunteer).build(),
                        TaskAssignment.builder().user(volunteer).build()))
                .build();

        CampaignResultDraftContext context = factory.create(
                Campaign.builder().target(0L).build(),
                500L,
                1L,
                1L,
                List.of(),
                List.of(),
                List.of(task));

        assertThat(context.goalPercent()).isZero();
        assertThat(context.taskCount()).isEqualTo(1L);
        assertThat(context.completedTaskCount()).isEqualTo(1L);
        assertThat(context.taskDescriptions()).singleElement().satisfies(description -> {
            assertThat(description).contains("task title \"Pack / deliver\"");
            assertThat(description).contains("status: Done");
            assertThat(description).contains("assignee(s): An Tran");
            assertThat(description).contains("…");
            assertThat(description).doesNotContain("|unsafe delimiter");
        });
    }
}
