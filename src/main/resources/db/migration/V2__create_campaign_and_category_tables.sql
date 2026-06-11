-- 1. Table Category
CREATE TABLE category (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(255),
    description TEXT
);

-- 2. Table Campaign
CREATE TABLE campaign (
    id               SERIAL PRIMARY KEY,
    title            VARCHAR(255),
    description      TEXT,
    user_id          INT,
    status           VARCHAR(50),   -- Enum: PENDING, APPROVED, REJECTED, COMPLETED
    start_date       DATE,
    end_date         DATE,
    target           DECIMAL(19, 2),
    priority         VARCHAR(50),   -- Enum: LOW, NORMAL, HIGH, URGENT
    approved_at      TIMESTAMP,
    rejection_reason TEXT,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    CONSTRAINT fk_campaign_user FOREIGN KEY (user_id) REFERENCES "user" (id)
);

-- 3. Table Campaign Category
CREATE TABLE campaign_category (
    id          SERIAL PRIMARY KEY,
    campaign_id INT,
    category_id INT,
    CONSTRAINT fk_cc_campaign FOREIGN KEY (campaign_id) REFERENCES campaign (id),
    CONSTRAINT fk_cc_category FOREIGN KEY (category_id) REFERENCES category (id)
);

-- 4. Table Campaign Media
CREATE TABLE campaign_media (
    id          SERIAL PRIMARY KEY,
    campaign_id INT,
    url         VARCHAR(255),
    media_type  VARCHAR(50),
    created_at  TIMESTAMP,
    CONSTRAINT fk_cm_campaign FOREIGN KEY (campaign_id) REFERENCES campaign (id)
);