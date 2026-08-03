package com.mgmtp.gives.scheduler;

import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class MediaCleanupScheduler {

    private final CampaignMediaRepository campaignMediaRepository;
    private final String uploadDir;

    public MediaCleanupScheduler(
            CampaignMediaRepository campaignMediaRepository,
            @Value("${app.media.upload-dir}") String uploadDir) {
        this.campaignMediaRepository = campaignMediaRepository;
        this.uploadDir = uploadDir;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupDeletedMedia() {
        log.info("Media cleanup started");
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(14);

            List<CampaignMedia> expired = campaignMediaRepository
                    .findByDeletedAtNotNullAndDeletedAtBefore(cutoff);

            log.info("Media cleanup found {} expired records", expired.size());

            int deleted = 0;
            int failed = 0;
            for (CampaignMedia record : expired) {
                Path trashPath = Paths.get(uploadDir).resolve("trash").resolve(record.getUrl());
                Path rootPath = Paths.get(uploadDir).resolve(record.getUrl());
                try {
                    if (Files.exists(trashPath)) {
                        Files.deleteIfExists(trashPath);
                    } else {
                        Files.deleteIfExists(rootPath);
                    }
                } catch (IOException e) {
                    log.warn("Could not delete file on disk: {}", record.getUrl(), e);
                    failed++;
                    continue;
                }
                campaignMediaRepository.delete(record);
                log.info("Hard deleted campaign media: {}", record.getUrl());
                deleted++;
            }

            log.info("Media cleanup completed: {} deleted, {} failed", deleted, failed);
        } catch (Exception e) {
            log.error("Error during media cleanup: {}", e.getMessage(), e);
        }
    }
}
