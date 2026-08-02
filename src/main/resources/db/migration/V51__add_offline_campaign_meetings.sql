ALTER TABLE campaign_meetings
    ADD COLUMN meeting_type VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
    ADD COLUMN location TEXT,
    ADD COLUMN calendar_uid VARCHAR(255),
    ADD COLUMN calendar_sequence INTEGER NOT NULL DEFAULT 0,
    ALTER COLUMN meeting_url DROP NOT NULL;

UPDATE campaign_meetings
SET calendar_uid = 'campaign-meeting-' || id || '@mgmgives'
WHERE calendar_uid IS NULL;

ALTER TABLE campaign_meetings
    ALTER COLUMN calendar_uid SET NOT NULL;

CREATE UNIQUE INDEX ux_campaign_meetings_calendar_uid ON campaign_meetings (calendar_uid);
