package com.mgmtp.gives.mapper;

import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.CampaignMemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DonationResponseMapperTest {

    @Mock
    CampaignMemberService campaignMemberService;

    @InjectMocks
    DonationResponseMapper mapper;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicResponseProtectsAnonymousAndHiddenDonationDetails() {
        Donation donation = donation();
        donation.setAnonymous(true);
        donation.setMessageHidden(true);

        var response = mapper.toResponse(donation);

        assertThat(response.getDonorName()).isEqualTo("Anonymous");
        assertThat(response.getDonorEmail()).isNull();
        assertThat(response.getAmount()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getCampaignName()).isEqualTo("Community support");
    }

    @Test
    void donorCanSeeTheirOwnAnonymousAndHiddenDonationDetails() {
        Donation donation = donation();
        donation.setAnonymous(true);
        donation.setMessageHidden(true);
        authenticate(donation.getUser());

        var response = mapper.toResponse(donation);

        assertThat(response.getDonorName()).isEqualTo("Anonymous");
        assertThat(response.getDonorEmail()).isEqualTo("donor@example.test");
        assertThat(response.getAmount()).isEqualTo(250_000L);
        assertThat(response.getMessage()).isEqualTo("Keep going");
    }

    @Test
    void campaignManagerCanSeeProtectedDonationDetails() {
        Donation donation = donation();
        User manager = user(5L, "Campaign manager", "manager@example.test", UserRole.USER);
        authenticate(manager);
        when(campaignMemberService.canManageCampaign(10L, manager)).thenReturn(true);

        var response = mapper.toResponse(donation);

        assertThat(response.getAmount()).isEqualTo(250_000L);
        assertThat(response.getDonorEmail()).isEqualTo("donor@example.test");
    }

    @Test
    void adminResponseIncludesActorAndTransactionDetailsWithoutSecurityContext() {
        Donation donation = donation();
        User confirmer = user(9L, "Approver", "approver@example.test", UserRole.USER);
        donation.setConfirmedBy(confirmer);
        donation.setTransactionId("payment-42");
        donation.setTransactionDescription("mgmGives 42");

        var response = mapper.toAdminResponse(donation);

        assertThat(response.getUserId()).isEqualTo(2L);
        assertThat(response.getConfirmedById()).isEqualTo(9L);
        assertThat(response.getConfirmedByName()).isEqualTo("Approver");
        assertThat(response.getTransactionId()).isEqualTo("payment-42");
        assertThat(response.getTransactionDescription()).isEqualTo("mgmGives 42");
    }

    private static Donation donation() {
        User creator = user(1L, "Creator", "creator@example.test", UserRole.USER);
        User donor = user(2L, "Donor", "donor@example.test", UserRole.USER);
        Campaign campaign = new Campaign();
        campaign.setId(10L);
        campaign.setTitle("Community support");
        campaign.setUser(creator);

        Donation donation = new Donation();
        donation.setId(20L);
        donation.setCampaign(campaign);
        donation.setUser(donor);
        donation.setType(DonationType.MONEY);
        donation.setStatus(DonationStatus.SUCCESSFUL);
        donation.setAmount(250_000L);
        donation.setMessage("Keep going");
        donation.setCreatedAt(LocalDateTime.of(2026, 8, 3, 12, 0));
        return donation;
    }

    private static User user(Long id, String name, String email, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }

    private static void authenticate(User user) {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUser()).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of()));
    }
}
