package com.mgmtp.gives.service;

import com.mgmtp.gives.entity.CampaignMeeting;
import com.mgmtp.gives.entity.User;

import java.util.List;

public interface CampaignMeetingInvitationService {
    void sendInvitations(CampaignMeeting meeting);

    void sendInvitations(CampaignMeeting meeting, List<User> recipients);

    void sendCancellationNotice(CampaignMeeting meeting);
}
