-- =============================================
-- ENUM TYPES
-- =============================================
CREATE TYPE campaign_member_role   AS ENUM ('CAMPAIGN_ADMIN', 'VOLUNTEER', 'DONOR');
CREATE TYPE campaign_member_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');
CREATE TYPE task_status            AS ENUM ('TODO', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');

-- 1. Table campaign_members
CREATE TABLE campaign_members (
    id               BIGSERIAL PRIMARY KEY,
    campaign_id      BIGINT NOT NULL,
    user_id          BIGINT NOT NULL,
    role_in_campaign campaign_member_role,
    status           campaign_member_status,
    joined_at        TIMESTAMP,
    CONSTRAINT fk_member_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT fk_member_user     FOREIGN KEY (user_id)     REFERENCES users (id)
);

-- 2. Table campaign_tasks
CREATE TABLE campaign_tasks (
    id          BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL,
    title       VARCHAR(255),
    description TEXT,
    status      task_status,
    due_date    TIMESTAMP,
    created_by  BIGINT NOT NULL,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    CONSTRAINT fk_task_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT fk_task_creator  FOREIGN KEY (created_by)  REFERENCES users (id)
);

-- 3. Table task_assignments
CREATE TABLE task_assignments (
    id           BIGSERIAL PRIMARY KEY,
    task_id      BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    status       task_status,
    assigned_at  TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT fk_ta_task FOREIGN KEY (task_id) REFERENCES campaign_tasks (id),
    CONSTRAINT fk_ta_user FOREIGN KEY (user_id) REFERENCES users (id)
);