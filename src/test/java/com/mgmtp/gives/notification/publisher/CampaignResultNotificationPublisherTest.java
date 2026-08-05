package com.mgmtp.gives.notification.publisher;

import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.dto.campaign.DonorNotificationInfo;
import com.mgmtp.gives.dto.campaign.DonorThankYouContext;
import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.repository.CampaignFollowerRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.service.EmailService;
import com.mgmtp.gives.service.GeminiService;
import com.mgmtp.gives.service.NotificationService;
import com.mgmtp.gives.service.support.CampaignResultPdfRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignResultNotificationPublisherTest {

    @Mock
    NotificationService notificationService;

    @Mock
    EmailService emailService;

    @Mock
    CampaignFollowerRepository campaignFollowerRepository;

    @Mock
    CampaignMemberRepository campaignMemberRepository;

    @Mock
    DonationRepository donationRepository;

    @Mock
    GeminiService geminiService;

    @Mock
    TemplateEngine templateEngine;

    @Mock
    MailProps mailProps;

    @Mock
    CampaignResultPdfRenderer pdfRenderer;

    @InjectMocks
    CampaignResultNotificationPublisher publisher;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = Campaign.builder().id(17L).title("School supplies").build();
        when(mailProps.getFrontendUrl()).thenReturn("https://gives.example.test");
        when(templateEngine.process(eq("campaign-result-notification"), any(Context.class)))
                .thenReturn("<p>Final report</p>");
    }

    @Test
    void mergesRecipientRolesAndSendsOneTailoredDeliveryPerUser() {
        User follower = user(1L, "Follower", "follower@example.test");
        User multiRoleSupporter = user(2L, "Volunteer Donor", "multi@example.test");
        byte[] pdf = {1, 2, 3};
        when(pdfRenderer.render(campaign)).thenReturn(pdf);
        when(campaignFollowerRepository.findFollowerUsersByCampaignId(17L))
                .thenReturn(List.of(follower, multiRoleSupporter));
        when(campaignMemberRepository.findUsersByCampaignIdAndRole(17L, CampaignMemberRole.VOLUNTEER))
                .thenReturn(List.of(multiRoleSupporter));
        when(donationRepository.findDonorNotificationInfoByCampaignId(17L, DonationStatus.SUCCESSFUL))
                .thenReturn(List.of(
                        new DonorNotificationInfo(2L, "multi@example.test", "Volunteer Donor", 250_000L),
                        new DonorNotificationInfo(3L, "goods@example.test", "Goods Donor", null)));

        publisher.publish(campaign);

        ArgumentCaptor<CreateNotificationCommand> commandCaptor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationService, times(3)).createNotification(commandCaptor.capture());
        Map<Long, CreateNotificationCommand> commandsByUser = commandCaptor.getAllValues().stream()
                .collect(Collectors.toMap(
                        command -> command.recipients().iterator().next().userId(), Function.identity()));

        assertThat(commandsByUser).containsOnlyKeys(1L, 2L, 3L);
        assertThat(commandsByUser.get(1L).message()).contains("following and supporting");
        assertThat(commandsByUser.get(2L).message())
                .contains("dedication and hard work")
                .contains("250,000 VND");
        assertThat(commandsByUser.get(3L).message()).contains("generous contribution");

        verify(emailService).sendHtmlEmail(
                "follower@example.test", "Campaign Results: School supplies", "<p>Final report</p>");
        verify(emailService).sendHtmlEmailWithAttachment(
                "multi@example.test",
                "Campaign Results: School supplies",
                "<p>Final report</p>",
                pdf,
                "final-report-17.pdf",
                "application/pdf");
        verify(emailService).sendHtmlEmailWithAttachment(
                "goods@example.test",
                "Campaign Results: School supplies",
                "<p>Final report</p>",
                pdf,
                "final-report-17.pdf",
                "application/pdf");
    }

    @Test
    void fallsBackToEmailWithoutAttachmentWhenPdfRenderingFails() {
        User volunteer = user(4L, "Volunteer", "volunteer@example.test");
        doThrow(new IllegalStateException("PDF unavailable")).when(pdfRenderer).render(campaign);
        when(campaignFollowerRepository.findFollowerUsersByCampaignId(17L)).thenReturn(List.of());
        when(campaignMemberRepository.findUsersByCampaignIdAndRole(17L, CampaignMemberRole.VOLUNTEER))
                .thenReturn(List.of(volunteer));
        when(donationRepository.findDonorNotificationInfoByCampaignId(17L, DonationStatus.SUCCESSFUL))
                .thenReturn(List.of());

        assertThatCode(() -> publisher.publish(campaign)).doesNotThrowAnyException();

        verify(notificationService).createNotification(any(CreateNotificationCommand.class));
        verify(emailService).sendHtmlEmail(
                "volunteer@example.test", "Campaign Results: School supplies", "<p>Final report</p>");
        verify(emailService, never()).sendHtmlEmailWithAttachment(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void buildsAiThankYouContextFromMoneyAndGoodsDonations() {
        User donor = user(5L, "Generous Donor", "donor@example.test");
        Donation money = Donation.builder().user(donor).type(DonationType.MONEY).amount(400_000L).build();
        Donation goods = Donation.builder()
                .user(donor)
                .type(DonationType.GOODS)
                .goodsCategory("Notebooks")
                .detail("40 packs")
                .build();
        when(pdfRenderer.render(campaign)).thenReturn(null);
        when(campaignFollowerRepository.findFollowerUsersByCampaignId(17L)).thenReturn(List.of());
        when(campaignMemberRepository.findUsersByCampaignIdAndRole(17L, CampaignMemberRole.VOLUNTEER))
                .thenReturn(List.of());
        when(donationRepository.findDonorNotificationInfoByCampaignId(17L, DonationStatus.SUCCESSFUL))
                .thenReturn(List.of(new DonorNotificationInfo(5L, "donor@example.test", "Generous Donor", 400_000L)));
        when(donationRepository.findByCampaignIdAndStatus(17L, DonationStatus.SUCCESSFUL))
                .thenReturn(List.of(money, goods));
        when(geminiService.isConfigured()).thenReturn(true);
        when(geminiService.generateDonorThankYouMessages(eq(campaign), anyList()))
                .thenReturn(Map.of(5L, "Thank you for your impact."));

        publisher.publish(campaign);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DonorThankYouContext>> contextCaptor = ArgumentCaptor.forClass(List.class);
        verify(geminiService).generateDonorThankYouMessages(eq(campaign), contextCaptor.capture());
        assertThat(contextCaptor.getValue()).singleElement().satisfies(context -> {
            assertThat(context.userId()).isEqualTo(5L);
            assertThat(context.fullName()).isEqualTo("Generous Donor");
            assertThat(context.totalMoneyAmount()).isEqualTo(400_000L);
            assertThat(context.donationCount()).isEqualTo(2);
            assertThat(context.goodsItems()).containsExactly("Notebooks - 40 packs");
        });
    }

    private static User user(Long id, String fullName, String email) {
        return User.builder().id(id).fullName(fullName).email(email).build();
    }
}
