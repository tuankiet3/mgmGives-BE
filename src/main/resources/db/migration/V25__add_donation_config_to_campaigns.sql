-- V25: Add donation config to campaigns and create campaign_qr_medias table
CREATE TYPE donation_method AS ENUM ('MANUAL_QR', 'PAYOS', 'HYBRID');

ALTER TABLE campaigns
    ADD COLUMN donation_method donation_method NOT NULL DEFAULT 'PAYOS',
    ADD COLUMN qr_bank_info    TEXT            NULL;

CREATE TABLE campaign_qr_medias (
    id           BIGSERIAL PRIMARY KEY,
    campaign_id  BIGINT NOT NULL UNIQUE,
    url          TEXT   NOT NULL,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    CONSTRAINT fk_qr_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id) ON DELETE CASCADE
);

CREATE INDEX idx_campaign_qr_medias_campaign_id ON campaign_qr_medias (campaign_id);
