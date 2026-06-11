-- =============================================
-- ENUM TYPES
-- =============================================
CREATE TYPE donation_type       AS ENUM ('MONEY', 'GOODS');
CREATE TYPE donation_status     AS ENUM ('PENDING', 'CONFIRMED', 'REJECTED', 'FAILED');
CREATE TYPE announcement_status AS ENUM ('DRAFT', 'PUBLISHED');

-- 1. Table donations
CREATE TABLE donations (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL,
    campaign_id    BIGINT NOT NULL,
    type           donation_type,
    amount         BIGINT NOT NULL DEFAULT 0,
    detail         TEXT,
    is_anonymous   BOOLEAN,
    status         donation_status,
    transaction_id VARCHAR(255),
    confirmed_by   BIGINT NOT NULL,
    created_at     TIMESTAMP,
    CONSTRAINT fk_donation_user         FOREIGN KEY (user_id)     REFERENCES users (id),
    CONSTRAINT fk_donation_campaign     FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT fk_donation_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES users (id)
);

-- 2. Table notifications
CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    title      VARCHAR(255) NOT NULL,
    message    TEXT,
    type       VARCHAR(50),
    is_read    BOOLEAN,
    link_url   VARCHAR(255),
    created_at TIMESTAMP,
    CONSTRAINT fk_noti_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 3. Table announcements
CREATE TABLE announcements (
    id           BIGSERIAL PRIMARY KEY,
    campaign_id  BIGINT NOT NULL,
    title        VARCHAR(255) NOT NULL,
    content      TEXT,
    created_by   BIGINT NOT NULL,
    status       announcement_status,
    published_at TIMESTAMP,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    CONSTRAINT fk_ann_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT fk_ann_creator  FOREIGN KEY (created_by)  REFERENCES users (id)
);