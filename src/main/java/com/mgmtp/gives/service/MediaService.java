package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMeeting;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.entity.User;
import org.springframework.web.multipart.MultipartFile;

public interface MediaService {

    CampaignMediaResponse uploadCampaignMedia(MultipartFile file, Long campaignId, boolean isCover, User currentUser);

    CampaignMediaResponse uploadCampaignMeetingAttachment(
            MultipartFile file,
            Campaign campaign,
            CampaignMeeting meeting
    );

    CampaignMediaResponse softDeleteCampaignMedia(Long id, User currentUser);

    CampaignMediaResponse softDeleteCampaignMeetingAttachment(CampaignMedia media);

    CampaignMedia restoreCampaignMedia(Long id);

    String uploadAvatar(MultipartFile file, User currentUser);

    void deleteAvatar(User currentUser);

    String uploadCampaignQr(MultipartFile file, User currentUser);

    String uploadTaskFile(MultipartFile file);

    String uploadTransactionProof(MultipartFile file);

    void softDeleteTaskFile(String storedFilename);
}
