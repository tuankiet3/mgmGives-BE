-- 1. Add the column as nullable first
ALTER TABLE campaign_medias ADD COLUMN is_cover BOOLEAN;

-- 2. Initialize all existing records to FALSE
UPDATE campaign_medias SET is_cover = FALSE;

-- 3. Set the oldest active image as the cover image for existing campaigns
UPDATE campaign_medias cm
SET is_cover = TRUE
WHERE cm.id IN (
    SELECT MIN(sub.id)
    FROM campaign_medias sub
    WHERE sub.media_type = 'IMAGE'
      AND sub.deleted_at IS NULL
    GROUP BY sub.campaign_id
);

-- 4. Apply DEFAULT constraint for future inserts
ALTER TABLE campaign_medias ALTER COLUMN is_cover SET DEFAULT FALSE;

-- 5. Apply NOT NULL constraint now that all data is populated
ALTER TABLE campaign_medias ALTER COLUMN is_cover SET NOT NULL;
