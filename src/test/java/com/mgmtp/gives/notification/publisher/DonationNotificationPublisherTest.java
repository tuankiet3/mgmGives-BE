package com.mgmtp.gives.notification.publisher;

import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DonationNotificationPublisherTest {

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    NotificationService notificationService;

    @Mock
    CampaignMemberRepository campaignMemberRepository;

    @InjectMocks
    DonationNotificationPublisher publisher;

    @Test
    void pendingApprovalNotifiesCreatorAndUniqueCampaignAdmins() {
        User creator = user(1L, "Creator", "creator@example.test");
        User admin = user(2L, "Admin", "admin@example.test");
        Donation donation = donation(creator, user(3L, "Donor", "donor@example.test"));
        donation.setAnonymous(true);
        when(campaignMemberRepository.findUsersByCampaignIdAndRole(10L, CampaignMemberRole.CAMPAIGN_ADMIN))
                .thenReturn(List.of(creator, admin));

        publisher.publishPendingApproval(donation);

        ArgumentCaptor<CreateNotificationCommand> captor = ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationService, org.mockito.Mockito.times(2)).createNotification(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(command -> {
                    assertThat(command.title()).isEqualTo("New Pending Donation");
                    assertThat(command.message())
                            .contains("Donor 'Anonymous'")
                            .contains("250.000 VND")
                            .contains("Community support");
                    assertThat(command.linkUrl()).isEqualTo("/campaigns/10/approvals");
                });
        assertThat(captor.getAllValues())
                .flatExtracting(CreateNotificationCommand::recipients)
                .extracting("userId")
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void rejectedDonationNotifiesDonorWithReviewLink() {
        Donation donation = donation(
                user(1L, "Creator", "creator@example.test"),
                user(3L, "Donor", "donor@example.test"));

        publisher.publishRejected(donation, "Receipt does not match");

        ArgumentCaptor<CreateNotificationCommand> captor = ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationService).createNotification(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("Donation Rejected");
        assertThat(captor.getValue().message())
                .contains("250.000 VND")
                .contains("Receipt does not match");
        assertThat(captor.getValue().linkUrl()).isEqualTo("/campaigns/10?rejectedDonationId=20");
    }

    @Test
    void rejectedDonationWithoutDonorDoesNotCreateNotification() {
        Donation donation = donation(user(1L, "Creator", "creator@example.test"), null);

        publisher.publishRejected(donation, "Invalid receipt");

        verify(notificationService, never()).createNotification(org.mockito.ArgumentMatchers.any());
    }

    private static Donation donation(User creator, User donor) {
        Campaign campaign = new Campaign();
        campaign.setId(10L);
        campaign.setTitle("Community support");
        campaign.setUser(creator);

        Donation donation = new Donation();
        donation.setId(20L);
        donation.setCampaign(campaign);
        donation.setUser(donor);
        donation.setType(DonationType.MONEY);
        donation.setAmount(250_000L);
        return donation;
    }

    private static User user(Long id, String name, String email) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        user.setEmail(email);
        return user;
    }
}
