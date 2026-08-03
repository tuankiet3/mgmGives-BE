-- Migration to update donations table for approval workflow
-- Make confirmed_by nullable for pending donations, add confirmed_at, reject_reason and updated_at columns

ALTER TABLE donations ALTER COLUMN confirmed_by DROP NOT NULL;

ALTER TABLE donations ADD COLUMN confirmed_at TIMESTAMP;
ALTER TABLE donations ADD COLUMN reject_reason TEXT;
ALTER TABLE donations ADD COLUMN updated_at TIMESTAMP DEFAULT NOW();