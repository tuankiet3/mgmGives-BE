package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignFollower;
import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Disabled("Requires a PostgreSQL test database on localhost:5432; Jenkins PR builds do not provide one.")
class CampaignFollowerRepositoryTest {

    @Autowired
    private CampaignFollowerRepository campaignFollowerRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findAllByUserIdWithFilters_requiresCampaignToContainAllSelectedCategories() {
        User user = persistUser("filter-user@example.com");
        Category disasterRelief = persistCategory("Disaster Relief");
        Category education = persistCategory("Education");
        Category healthcare = persistCategory("Healthcare");

        Campaign disasterAndEducation = persistCampaign(
                "Disaster and Education",
                user,
                Set.of(disasterRelief, education)
        );
        Campaign educationAndHealthcare = persistCampaign(
                "Education and Healthcare",
                user,
                Set.of(education, healthcare)
        );
        Campaign allCategories = persistCampaign(
                "All Categories",
                user,
                Set.of(disasterRelief, education, healthcare)
        );

        follow(user, disasterAndEducation);
        follow(user, educationAndHealthcare);
        follow(user, allCategories);
        entityManager.flush();
        entityManager.clear();

        Page<CampaignFollower> disasterAndEducationPage = findFollowedCampaigns(
                user.getId(),
                true,
                List.of(disasterRelief.getId(), education.getId()),
                2L
        );

        assertThat(disasterAndEducationPage.getContent())
                .extracting(follower -> follower.getCampaign().getTitle())
                .containsExactlyInAnyOrder("Disaster and Education", "All Categories");

        Page<CampaignFollower> educationAndHealthcarePage = findFollowedCampaigns(
                user.getId(),
                true,
                List.of(education.getId(), healthcare.getId()),
                2L
        );

        assertThat(educationAndHealthcarePage.getContent())
                .extracting(follower -> follower.getCampaign().getTitle())
                .containsExactlyInAnyOrder("Education and Healthcare", "All Categories");

        Page<CampaignFollower> allCategoriesPage = findFollowedCampaigns(
                user.getId(),
                true,
                List.of(disasterRelief.getId(), education.getId(), healthcare.getId()),
                3L
        );

        assertThat(allCategoriesPage.getContent())
                .extracting(follower -> follower.getCampaign().getTitle())
                .containsExactly("All Categories");

        Page<CampaignFollower> educationOnlyPage = findFollowedCampaigns(
                user.getId(),
                true,
                List.of(education.getId()),
                1L
        );

        assertThat(educationOnlyPage.getContent())
                .extracting(follower -> follower.getCampaign().getTitle())
                .containsExactlyInAnyOrder(
                        "Disaster and Education",
                        "Education and Healthcare",
                        "All Categories"
                );

        Page<CampaignFollower> noCategoryFilterPage = findFollowedCampaigns(
                user.getId(),
                false,
                List.of(-1L),
                0L
        );

        assertThat(noCategoryFilterPage.getContent())
                .extracting(follower -> follower.getCampaign().getTitle())
                .containsExactlyInAnyOrder(
                        "Disaster and Education",
                        "Education and Healthcare",
                        "All Categories"
                );
    }

    private Page<CampaignFollower> findFollowedCampaigns(
            Long userId,
            boolean hasCategories,
            List<Long> categoryIds,
            long categoryCount
    ) {
        return campaignFollowerRepository.findAllByUserIdWithFilters(
                userId,
                "",
                null,
                null,
                hasCategories,
                categoryIds,
                categoryCount,
                PageRequest.of(0, 10)
        );
    }

    private User persistUser(String email) {
        User user = User.builder()
                .email(email)
                .fullName("Filter User")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        entityManager.persist(user);
        return user;
    }

    private Category persistCategory(String name) {
        Category category = Category.builder()
                .name(name)
                .description(name)
                .build();
        entityManager.persist(category);
        return category;
    }

    private Campaign persistCampaign(String title, User user, Set<Category> categories) {
        Campaign campaign = Campaign.builder()
                .title(title)
                .description(title)
                .user(user)
                .status(CampaignStatus.IN_PROGRESS)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .target(1_000_000L)
                .priority(CampaignPriority.NORMAL)
                .categories(categories)
                .build();
        entityManager.persist(campaign);
        return campaign;
    }

    private void follow(User user, Campaign campaign) {
        CampaignFollower follower = CampaignFollower.builder()
                .user(user)
                .campaign(campaign)
                .build();
        entityManager.persist(follower);
    }
}
