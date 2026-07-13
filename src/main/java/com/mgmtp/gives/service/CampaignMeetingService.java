package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.dto.campaign_meeting.CampaignMeetingResponse;
import com.mgmtp.gives.dto.campaign_meeting.CampaignMeetingRecipientResponse;
import com.mgmtp.gives.dto.campaign_meeting.CreateCampaignMeetingRequest;
import com.mgmtp.gives.dto.campaign_meeting.MeetingActivityResponse;
import com.mgmtp.gives.dto.campaign_meeting.MeetingNotesResponse;
import com.mgmtp.gives.dto.campaign_meeting.UpdateCampaignMeetingRequest;
import com.mgmtp.gives.dto.campaign_meeting.UpdateMeetingNotesRequest;
import com.mgmtp.gives.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CampaignMeetingService {
    CampaignMeetingResponse createMeeting(Long campaignId, CreateCampaignMeetingRequest request, User currentUser);

    List<CampaignMeetingResponse> getMeetings(Long campaignId, String view, User currentUser);

    CampaignMeetingResponse getMeeting(Long campaignId, Long meetingId, User currentUser);

    CampaignMeetingResponse updateMeeting(
            Long campaignId,
            Long meetingId,
            UpdateCampaignMeetingRequest request,
            User currentUser
    );

    CampaignMeetingResponse updateMeetingStatus(Long campaignId, Long meetingId, User currentUser);

    List<CampaignMeetingRecipientResponse> getMeetingRecipients(Long campaignId, User currentUser);

    List<CampaignMeetingRecipientResponse> getInvitedMembers(Long campaignId, Long meetingId, User currentUser);

    MeetingNotesResponse getMeetingNotes(Long campaignId, Long meetingId, User currentUser);

    MeetingNotesResponse updateMeetingNotes(
            Long campaignId,
            Long meetingId,
            UpdateMeetingNotesRequest request,
            User currentUser
    );

    List<MeetingActivityResponse> getMeetingActivity(Long campaignId, Long meetingId, User currentUser);

    List<CampaignMediaResponse> getMeetingAttachments(Long campaignId, Long meetingId, User currentUser);

    CampaignMediaResponse uploadMeetingAttachment(
            Long campaignId,
            Long meetingId,
            MultipartFile file,
            User currentUser
    );

    CampaignMediaResponse deleteMeetingAttachment(
            Long campaignId,
            Long meetingId,
            Long attachmentId,
            User currentUser
    );

    CampaignMeetingResponse cancelMeeting(Long campaignId, Long meetingId, User currentUser);
}
