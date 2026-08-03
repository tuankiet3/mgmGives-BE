-- Add approved_by column to track which admin approved or rejected a campaign
ALTER TABLE campaigns ADD COLUMN approved_by BIGINT REFERENCES users(id);
