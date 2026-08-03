-- Add deleted_at column to campaign_medias table for soft delete functionality
ALTER TABLE campaign_medias
ADD COLUMN deleted_at TIMESTAMP WITHOUT TIME ZONE;
