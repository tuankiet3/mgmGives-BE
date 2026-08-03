-- Migration V30: Add transaction_description and transaction_proof_url to donations, and bank_code and bank_bin to campaigns
ALTER TABLE donations
    ADD COLUMN IF NOT EXISTS transaction_description VARCHAR(255),
    ADD COLUMN IF NOT EXISTS transaction_proof_url VARCHAR(255);

ALTER TABLE campaigns
    ADD COLUMN IF NOT EXISTS bank_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS bank_bin VARCHAR(50),
    DROP COLUMN IF EXISTS qr_bank_info;

DROP TABLE IF EXISTS campaign_qr_medias CASCADE;
