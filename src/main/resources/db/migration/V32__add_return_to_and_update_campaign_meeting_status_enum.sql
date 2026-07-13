ALTER TABLE webex_oauth_states
    ADD COLUMN return_to VARCHAR(512);

ALTER TABLE campaign_meetings
    ALTER COLUMN status DROP DEFAULT;

ALTER TYPE campaign_meeting_status RENAME TO campaign_meeting_status_old;

CREATE TYPE campaign_meeting_status AS ENUM (
    'UPCOMING',
    'IN_PROGRESS',
    'ENDED',
    'EXPIRED',
    'CANCELLED'
);

ALTER TABLE campaign_meetings
    ALTER COLUMN status TYPE campaign_meeting_status
    USING (
        CASE status::text
            WHEN 'SCHEDULED' THEN 'UPCOMING'
            ELSE status::text
        END
    )::campaign_meeting_status;

ALTER TABLE campaign_meetings
    ALTER COLUMN status SET DEFAULT 'UPCOMING';

DROP TYPE campaign_meeting_status_old;
