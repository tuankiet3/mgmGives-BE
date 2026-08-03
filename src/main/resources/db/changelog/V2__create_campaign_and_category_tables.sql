-- =============================================
-- ENUM TYPES
-- =============================================
CREATE TYPE campaign_status   AS ENUM ('PENDING', 'APPROVED', 'IN_PROGRESS', 'REJECTED', 'COMPLETED');
CREATE TYPE campaign_priority AS ENUM ('LOW', 'NORMAL', 'HIGH', 'URGENT');

-- 1. Table categories
CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT
);

-- 2. Table campaigns
CREATE TABLE campaigns (
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    user_id          BIGINT NOT NULL,
    status           campaign_status,
    start_date       TIMESTAMP,
    end_date         TIMESTAMP,
    target           BIGINT,
    priority         campaign_priority,
    approved_at      TIMESTAMP,
    rejection_reason TEXT,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    CONSTRAINT fk_campaign_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 3. Join table campaign_categories (Campaign <-> Category many-to-many)
CREATE TABLE campaign_categories (
    campaign_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (campaign_id, category_id),
    CONSTRAINT fk_cc_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT fk_cc_category FOREIGN KEY (category_id) REFERENCES categories (id)
);

-- 4. Table campaign_medias
CREATE TABLE campaign_medias (
    id          BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL,
    url         VARCHAR(255),
    media_type  VARCHAR(50),
    created_at  TIMESTAMP,
    CONSTRAINT fk_cm_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id)
);