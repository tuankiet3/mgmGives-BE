-- Add structured bank account fields to campaigns table for Manual QR donation method
ALTER TABLE campaigns
    ADD COLUMN IF NOT EXISTS bank_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS bank_account_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS bank_account_holder_name VARCHAR(150);
