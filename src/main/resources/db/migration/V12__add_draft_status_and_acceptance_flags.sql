-- V13: Add draft status to campaign_status enum and donation acceptance flags to campaigns

ALTER TYPE campaign_status ADD VALUE 'DRAFT';

ALTER TABLE campaigns 
ADD COLUMN accepts_money BOOLEAN DEFAULT TRUE,
ADD COLUMN accepts_goods BOOLEAN DEFAULT TRUE;
