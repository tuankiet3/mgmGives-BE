-- Add order_code column to donations table to persist the PayOS unique order code
ALTER TABLE donations ADD COLUMN order_code BIGINT;
