package com.mgmtp.gives.config;

import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.CategoryRepository;
import com.mgmtp.gives.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class CampaignSeeder implements CommandLineRunner {

    private final CampaignRepository campaignRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        if (campaignRepository.count() > 0) {
            log.info("Campaigns already exist. Skipping campaign seed.");
            return;
        }

        User creator = userRepository.findAll().stream()
                .findFirst()
                .orElse(null);

        if (creator == null) {
            log.warn("No users found to set as campaign creator. Skipping campaign seed.");
            return;
        }

        log.info("Seeding default categories and campaigns...");

        // 1. Seed Categories if not exist
        Category disasterRelief = getOrCreateCategory("Disaster Relief", "Emergency response and recovery support for natural disasters.");
        Category education = getOrCreateCategory("Education", "Providing books, scholarships, and resources to children in need.");
        Category healthcare = getOrCreateCategory("Healthcare", "Medical aid, supplies, and services for underprivileged communities.");

        // 2. Seed Campaigns
        LocalDateTime now = LocalDateTime.now();

        // Campaign 1: Active and Urgent
        Campaign campaign1 = Campaign.builder()
                .title("MGM Books & Warmth 2026")
                .description("Providing school supplies, books, and warm winter jackets to children in Yen Bai province before the cold season starts.")
                .user(creator)
                .status(CampaignStatus.APPROVED)
                .startDate(now.minusDays(5))
                .endDate(now.plusDays(30))
                .target(120000000L) // 120 million VND
                .priority(CampaignPriority.URGENT)
                .categories(Set.of(education, disasterRelief))
                .build();

        // Campaign 2: Active
        Campaign campaign2 = Campaign.builder()
                .title("Clean Water for Central Vietnam")
                .description("Installing purification systems and digging wells in remote villages affected by severe droughts and water salinization.")
                .user(creator)
                .status(CampaignStatus.APPROVED)
                .startDate(now.minusDays(10))
                .endDate(now.plusDays(45))
                .target(250000000L) // 250 million VND
                .priority(CampaignPriority.HIGH)
                .categories(Set.of(disasterRelief, healthcare))
                .build();

        // Campaign 3: Completed/Closed
        Campaign campaign3 = Campaign.builder()
                .title("Mid-Autumn Festival Smiles 2025")
                .description("Brought mooncakes, lanterns, and happiness to children in pediatric wards across central hospitals during the festival.")
                .user(creator)
                .status(CampaignStatus.COMPLETED)
                .startDate(now.minusDays(300))
                .endDate(now.minusDays(270))
                .target(50000000L) // 50 million VND
                .priority(CampaignPriority.NORMAL)
                .categories(Set.of(healthcare, education))
                .build();

        campaignRepository.saveAll(List.of(campaign1, campaign2, campaign3));
        log.info("Default categories and campaigns seeded successfully!");
    }

    private Category getOrCreateCategory(String name, String description) {
        return categoryRepository.findAll().stream()
                .filter(cat -> cat.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name(name)
                        .description(description)
                        .campaigns(new HashSet<>())
                        .build()));
    }
}
