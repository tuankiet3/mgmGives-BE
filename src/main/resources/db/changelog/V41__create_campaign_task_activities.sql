CREATE TABLE campaign_task_activities (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor_id BIGINT,
    actor_name VARCHAR(255) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_campaign_task_activity_task
        FOREIGN KEY (task_id) REFERENCES campaign_tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_task_activity_actor
        FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_campaign_task_activities_timeline
    ON campaign_task_activities (task_id, created_at DESC, id DESC);

INSERT INTO campaign_task_activities (task_id, action, actor_id, actor_name, details, created_at)
SELECT task.id,
       'TASK_CREATED',
       task.created_by,
       COALESCE(NULLIF(created_by.full_name, ''), created_by.email, 'Unknown user'),
       jsonb_build_object('status', task.status::text),
       COALESCE(task.created_at, CURRENT_TIMESTAMP)
  FROM campaign_tasks task
  LEFT JOIN users created_by ON created_by.id = task.created_by
 WHERE NOT EXISTS (
       SELECT 1
         FROM campaign_task_activities activity
        WHERE activity.task_id = task.id
          AND activity.action = 'TASK_CREATED'
 );
