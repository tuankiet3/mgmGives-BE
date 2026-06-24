package com.mgmtp.gives.service;

import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.entity.User;
import org.springframework.web.multipart.MultipartFile;

public interface MediaService {

    CampaignMedia uploadCampaignMedia(MultipartFile file, Long campaignId);

    CampaignMedia softDeleteCampaignMedia(Long id);

    CampaignMedia restoreCampaignMedia(Long id);

    String uploadAvatar(MultipartFile file, User currentUser);

    void deleteAvatar(User currentUser);
}
