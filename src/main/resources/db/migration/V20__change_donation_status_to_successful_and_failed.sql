-- flyway:no-transaction

-- Step 1: Add the new SUCCESSFUL value to the enum type donation_status
ALTER TYPE donation_status ADD VALUE IF NOT EXISTS 'SUCCESSFUL';

-- Explicitly commit so the database registers the new enum value for the session
COMMIT;

-- Step 2: Migrate existing COMPLETED, CONFIRMED records to SUCCESSFUL
UPDATE donations SET status = 'SUCCESSFUL' WHERE status::text IN ('COMPLETED', 'CONFIRMED');

-- Step 3: Migrate any other pending or rejected records to FAILED
UPDATE donations SET status = 'FAILED' WHERE status::text IN ('PENDING', 'REJECTED');

