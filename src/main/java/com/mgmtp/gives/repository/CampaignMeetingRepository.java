package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.CampaignMeeting;
import com.mgmtp.gives.enums.CampaignMeetingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignMeetingRepository extends JpaRepository<CampaignMeeting, Long> {
    List<CampaignMeeting> findByCampaignIdOrderByStartTimeAsc(Long campaignId);

    List<CampaignMeeting> findByCampaignIdAndStatusOrderByStartTimeAsc(
            Long campaignId,
            CampaignMeetingStatus status
    );
}
