package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMeeting;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.entity.CampaignSpending;
import com.mgmtp.gives.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface MediaService {

    CampaignMediaResponse uploadCampaignMedia(MultipartFile file, Long campaignId, boolean isCover, String context, User currentUser);

    /**
     * Reconciles which CampaignMedia rows are tagged into a feature (an announcement, a final
     * report, ...): media in {@code currentlyTagged} but no longer in {@code requestedMediaIds}
     * is reverted via {@code onRevert}; every id in {@code requestedMediaIds} (in that order, so
     * callers can derive display order) is validated to exist, not be deleted, and belong to the
     * campaign, then claimed via {@code onClaim}. Returns the final claimed set in request order.
     * A null {@code requestedMediaIds} is a no-op that returns {@code currentlyTagged} unchanged.
     */
    List<CampaignMedia> reconcileMediaTags(
            Long campaignId,
            List<CampaignMedia> currentlyTagged,
            List<Long> requestedMediaIds,
            Consumer<CampaignMedia> onRevert,
            BiConsumer<CampaignMedia, Integer> onClaim
    );

    CampaignMediaResponse uploadCampaignMeetingAttachment(
            MultipartFile file,
            Campaign campaign,
            CampaignMeeting meeting
    );

    CampaignMediaResponse uploadCampaignSpendingAttachment(
            MultipartFile file,
            Campaign campaign,
            CampaignSpending spending
    );

    CampaignMediaResponse softDeleteCampaignMedia(Long id, User currentUser);

    CampaignMediaResponse softDeleteCampaignMeetingAttachment(CampaignMedia media);

    CampaignMediaResponse softDeleteCampaignSpendingAttachment(CampaignMedia media);

    CampaignMedia restoreCampaignMedia(Long id);

    String uploadAvatar(MultipartFile file, User currentUser);

    void deleteAvatar(User currentUser);

    String uploadCampaignQr(MultipartFile file, User currentUser);

    String uploadTaskFile(MultipartFile file);

    String uploadTransactionProof(MultipartFile file);

    void softDeleteTaskFile(String storedFilename);
}
