ALTER TABLE users
ADD COLUMN failed_attempt_count INT DEFAULT 0,
ADD COLUMN locked_until TIMESTAMP;