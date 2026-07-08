-- Drop status column from announcements
ALTER TABLE announcements DROP COLUMN status;

-- Add announcement_id column as nullable
ALTER TABLE campaign_medias ADD COLUMN announcement_id BIGINT;

-- Add foreign key constraint to announcements table with ON DELETE SET NULL
ALTER TABLE campaign_medias ADD CONSTRAINT fk_campaign_media_announcement
    FOREIGN KEY (announcement_id)
    REFERENCES announcements(id)
    ON DELETE SET NULL;

-- Create index on announcement_id for performance
CREATE INDEX idx_campaign_media_announcement ON campaign_medias(announcement_id);
