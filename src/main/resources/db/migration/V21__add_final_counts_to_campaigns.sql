ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS final_donor_count BIGINT;
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS final_volunteer_count BIGINT;
