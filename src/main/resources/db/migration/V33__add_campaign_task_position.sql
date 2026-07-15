ALTER TABLE campaign_tasks
    ADD COLUMN position BIGINT NOT NULL DEFAULT 0;

WITH ordered_tasks AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY campaign_id, status
               ORDER BY created_at ASC, id ASC
           ) AS next_position
    FROM campaign_tasks
)
UPDATE campaign_tasks task
SET position = ordered_tasks.next_position
FROM ordered_tasks
WHERE task.id = ordered_tasks.id;

CREATE INDEX idx_campaign_tasks_campaign_status_position
    ON campaign_tasks (campaign_id, status, position);
