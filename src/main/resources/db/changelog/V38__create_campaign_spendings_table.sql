-- Campaign spending ledger: admins log money spent on campaign activities so donors can
-- track fund usage in real time. Soft-deleted rows are kept for audit history.
CREATE TABLE campaign_spendings (
    id          BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL,
    amount      BIGINT NOT NULL,
    description TEXT NOT NULL,
    spent_at    DATE NOT NULL,
    created_by  BIGINT NOT NULL,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    deleted_at  TIMESTAMP,
    CONSTRAINT fk_spending_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT fk_spending_creator  FOREIGN KEY (created_by)  REFERENCES users (id),
    CONSTRAINT chk_spending_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_campaign_spending_campaign ON campaign_spendings (campaign_id);

-- Supporting photos reuse the existing campaign_medias table, following the same
-- nullable-FK-per-attachment-type pattern already used for meeting_id / announcement_id.
ALTER TABLE campaign_medias ADD COLUMN spending_id BIGINT;

ALTER TABLE campaign_medias ADD CONSTRAINT fk_campaign_media_spending
    FOREIGN KEY (spending_id)
    REFERENCES campaign_spendings (id)
    ON DELETE SET NULL;

CREATE INDEX idx_campaign_media_spending ON campaign_medias (spending_id);
