ALTER TABLE campaign_members
    DROP COLUMN status;

DROP TYPE IF EXISTS campaign_member_status;

ALTER TABLE campaign_members
    ADD CONSTRAINT uq_campaign_members_campaign_user UNIQUE (campaign_id, user_id);
