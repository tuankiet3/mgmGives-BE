-- 1. Table Campaign Member 
CREATE TABLE campaign_member (
    id                 SERIAL PRIMARY KEY,
    campaign_id        INT,
    user_id            INT,
    role_in_campaign   VARCHAR(50),  -- Enum: VOLUNTEER, DONATOR
    status             VARCHAR(50),  -- Enum: PENDING, APPROVED, REJECTED
    joined_at          TIMESTAMP,
    CONSTRAINT fk_member_campaign FOREIGN KEY (campaign_id) REFERENCES campaign (id),
    CONSTRAINT fk_member_user     FOREIGN KEY (user_id)     REFERENCES "user" (id)
);

-- 2. Table Campaign Task
CREATE TABLE campaign_task (
    id          SERIAL PRIMARY KEY,
    campaign_id INT,
    title       VARCHAR(255),
    description TEXT,
    status      VARCHAR(50),  -- Enum: TODO, IN_PROGRESS, REVIEW, DONE
    due_date    DATE,
    created_by  INT,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    CONSTRAINT fk_task_campaign FOREIGN KEY (campaign_id) REFERENCES campaign (id),
    CONSTRAINT fk_task_creator  FOREIGN KEY (created_by)  REFERENCES "user" (id)
);

-- 3. Table Task Assignment
CREATE TABLE task_assignment (
    id           SERIAL PRIMARY KEY,
    task_id      INT,
    user_id      INT,
    status       VARCHAR(50),  -- Enum: TODO, IN_PROGRESS, REVIEW, DONE
    assigned_at  TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT fk_ta_task FOREIGN KEY (task_id) REFERENCES campaign_task (id),
    CONSTRAINT fk_ta_user FOREIGN KEY (user_id) REFERENCES "user" (id)
);