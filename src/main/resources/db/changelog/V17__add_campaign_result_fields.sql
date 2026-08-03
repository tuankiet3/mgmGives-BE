-- Add result fields to campaigns table
ALTER TABLE campaigns
    ADD COLUMN result_posted        BOOLEAN   DEFAULT FALSE,
    ADD COLUMN result_summary       TEXT,
    ADD COLUMN final_amount_raised  BIGINT,
    ADD COLUMN items_summary        TEXT,
    ADD COLUMN acknowledgements     TEXT,
    ADD COLUMN result_published_by  BIGINT REFERENCES users(id),
    ADD COLUMN result_published_at  TIMESTAMP;

-- Add result media fields to campaign_medias table
ALTER TABLE campaign_medias
    ADD COLUMN caption       TEXT,
    ADD COLUMN display_order INT,
    ADD COLUMN context       VARCHAR(20) DEFAULT 'CAMPAIGN';
