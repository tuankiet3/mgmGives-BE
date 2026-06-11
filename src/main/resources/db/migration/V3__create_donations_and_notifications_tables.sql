-- 1. Table Donations
CREATE TABLE donations (
    id             SERIAL PRIMARY KEY,
    user_id        INT,
    campaign_id    INT,
    type           VARCHAR(50),   -- Enum: MONEY, ITEM
    amount         DECIMAL(19, 2),
    detail         TEXT,
    is_anonymous   BOOLEAN,
    status         VARCHAR(50),   -- Enum: PENDING, RECEIVED, DISTRIBUTED, CANCELLED
    transaction_id VARCHAR(255),
    confirmed_by   INT,
    created_at     TIMESTAMP,
    CONSTRAINT fk_donation_user         FOREIGN KEY (user_id)     REFERENCES "user" (id),
    CONSTRAINT fk_donation_campaign     FOREIGN KEY (campaign_id) REFERENCES campaign (id),
    CONSTRAINT fk_donation_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES "user" (id)
);

-- 2. Table Notification
CREATE TABLE notification (
    id         SERIAL PRIMARY KEY,
    user_id    INT,
    title      VARCHAR(255),
    message    TEXT,
    type       VARCHAR(50),
    is_read    BOOLEAN,
    link_url   VARCHAR(255),
    created_at TIMESTAMP,
    CONSTRAINT fk_noti_user FOREIGN KEY (user_id) REFERENCES "user" (id)
);

-- 3. Table Announcement
CREATE TABLE announcement (
    id           SERIAL PRIMARY KEY,
    campaign_id  INT,
    title        VARCHAR(255),
    content      TEXT,
    created_by   INT,
    status       VARCHAR(50),  -- Enum: DRAFT, PUBLISHED
    published_at TIMESTAMP,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    CONSTRAINT fk_ann_campaign FOREIGN KEY (campaign_id) REFERENCES campaign (id),
    CONSTRAINT fk_ann_creator  FOREIGN KEY (created_by)  REFERENCES "user" (id)
);