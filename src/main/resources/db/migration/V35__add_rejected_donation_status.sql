-- flyway:no-transaction
ALTER TYPE donation_status ADD VALUE IF NOT EXISTS 'REJECTED';
