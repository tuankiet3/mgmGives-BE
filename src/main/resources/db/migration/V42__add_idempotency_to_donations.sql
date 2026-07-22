ALTER TABLE donations ADD COLUMN idempotency_key VARCHAR(255);
ALTER TABLE donations ADD CONSTRAINT uq_donations_idempotency_key UNIQUE (idempotency_key);
