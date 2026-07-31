ALTER TABLE campaigns ALTER COLUMN donation_method SET DEFAULT 'MANUAL_QR'::donation_method;

-- Rename FAILED to CANCELLED in donation_status enum
ALTER TYPE donation_status RENAME VALUE 'FAILED' TO 'CANCELLED';
