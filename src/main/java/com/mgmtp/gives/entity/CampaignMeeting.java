package com.mgmtp.gives.entity;

import com.mgmtp.gives.enums.CampaignMeetingStatus;
import com.mgmtp.gives.enums.CampaignMeetingType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "campaign_meetings")
public class CampaignMeeting extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "webex_meeting_id")
    private String webexMeetingId;

    @Column(name = "calendar_uid", unique = true, nullable = false)
    private String calendarUid;

    @Column(name = "calendar_sequence", nullable = false)
    private Integer calendarSequence;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "meeting_url", columnDefinition = "TEXT")
    private String meetingUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_type")
    private CampaignMeetingType meetingType;

    @Column(columnDefinition = "TEXT")
    private String location;

    @Column(name = "notify_all")
    private boolean notifyAll;

    @Column(name = "invited_user_ids", columnDefinition = "TEXT")
    private String invitedUserIds;

    @Column(name = "invited_count")
    private Integer invitedCount;

    @Column(name = "invitations_sent_at")
    private LocalDateTime invitationsSentAt;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "campaign_meeting_status")
    private CampaignMeetingStatus status;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @ManyToOne
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "notes_updated_at")
    private LocalDateTime notesUpdatedAt;

    @ManyToOne
    @JoinColumn(name = "notes_updated_by")
    private User notesUpdatedBy;
}
