-- A nullable self-reference keeps reply context flat and allows top-level replies.
ALTER TABLE announcement_replies
    ADD COLUMN in_reply_to_id BIGINT;

ALTER TABLE announcement_replies
    ADD CONSTRAINT fk_reply_in_reply_to
    FOREIGN KEY (in_reply_to_id) REFERENCES announcement_replies(id) ON DELETE SET NULL;

-- Supports contextual-reference lookups without duplicating V27's active-reply pagination index.
CREATE INDEX IF NOT EXISTS idx_announcement_replies_in_reply_to
    ON announcement_replies(in_reply_to_id)
    WHERE in_reply_to_id IS NOT NULL;
