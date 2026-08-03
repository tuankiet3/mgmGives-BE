ALTER TABLE campaign_tasks
    ADD COLUMN IF NOT EXISTS version BIGINT;

UPDATE campaign_tasks
SET version = 0
WHERE version IS NULL;

ALTER TABLE campaign_tasks
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;

CREATE SEQUENCE IF NOT EXISTS campaign_task_position_seq;

SELECT setval(
    'campaign_task_position_seq',
    GREATEST(
        COALESCE((SELECT MAX(position) FROM campaign_tasks), 0) + 1,
        (SELECT last_value FROM campaign_task_position_seq) + 1
    ),
    false
);

ALTER TABLE campaign_tasks
    ALTER COLUMN position SET DEFAULT nextval('campaign_task_position_seq');

CREATE INDEX IF NOT EXISTS idx_campaign_tasks_board_order
    ON campaign_tasks (campaign_id, is_archived, status, position, id)
    WHERE deleted_at IS NULL;
