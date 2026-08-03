-- Create table to manage Campaign Task Labels and mapping table for Tasks
CREATE TABLE campaign_task_labels (
                                 id BIGSERIAL PRIMARY KEY,
                                 campaign_id BIGINT NOT NULL,
                                 name VARCHAR(255) NOT NULL,
                                 color VARCHAR(50) NOT NULL,
                                 CONSTRAINT fk_campaign_task_labels_campaign_id FOREIGN KEY (campaign_id) REFERENCES campaigns (id) ON DELETE CASCADE
);

CREATE INDEX idx_campaign_task_labels_campaign_id ON campaign_task_labels(campaign_id);

CREATE TABLE task_labels (
                             task_id BIGINT NOT NULL,
                             label_id BIGINT NOT NULL,
                             PRIMARY KEY (task_id, label_id),
                             CONSTRAINT fk_task_labels_task_id FOREIGN KEY (task_id) REFERENCES campaign_tasks (id) ON DELETE CASCADE,
                             CONSTRAINT fk_task_labels_label_id FOREIGN KEY (label_id) REFERENCES campaign_task_labels (id) ON DELETE CASCADE
);

CREATE INDEX idx_task_labels_label_id ON task_labels(label_id);

-- Update task status enum type
ALTER TYPE task_status RENAME TO task_status_old;

-- Create the new enum type with only the desired values
CREATE TYPE task_status AS ENUM (
    'TODO',
    'IN_PROGRESS',
    'DONE'
);

ALTER TABLE campaign_tasks
    ALTER COLUMN status TYPE task_status
    USING (
        CASE
            WHEN status::text = 'COMPLETED' THEN 'DONE'
            WHEN status::text = 'CANCELLED' THEN 'DONE'
            ELSE status::text
        END
    )::task_status;

ALTER TABLE task_assignments
    ALTER COLUMN status TYPE task_status
    USING (
        CASE
            WHEN status::text = 'COMPLETED' THEN 'DONE'
            WHEN status::text = 'CANCELLED' THEN 'DONE'
            ELSE status::text
        END
    )::task_status;

DROP TYPE task_status_old;

-- Add is_archived and deleted_at columns for Archive/Restore/Soft Delete features
ALTER TABLE campaign_tasks ADD COLUMN is_archived BOOLEAN DEFAULT FALSE;
ALTER TABLE campaign_tasks ADD COLUMN deleted_at TIMESTAMP;

CREATE TABLE task_attachments (
                                  id BIGSERIAL PRIMARY KEY,
                                  task_id BIGINT NOT NULL,
                                  original_filename VARCHAR(255) NOT NULL,
                                  stored_filename VARCHAR(255) NOT NULL,
                                  file_type VARCHAR(50) NOT NULL,
                                  file_size BIGINT NOT NULL,
                                  uploaded_by BIGINT NOT NULL,
                                  uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  CONSTRAINT fk_task_attachments_task_id FOREIGN KEY (task_id) REFERENCES campaign_tasks (id) ON DELETE CASCADE,
                                  CONSTRAINT fk_task_attachments_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id)
);
CREATE INDEX idx_task_attachments_task_id ON task_attachments(task_id);