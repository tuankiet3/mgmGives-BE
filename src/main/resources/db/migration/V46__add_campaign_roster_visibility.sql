CREATE TYPE member_list_visibility AS ENUM ('MEMBERS_ONLY', 'PUBLIC');

ALTER TABLE campaigns
    ADD COLUMN member_list_visibility member_list_visibility NOT NULL DEFAULT 'MEMBERS_ONLY';

ALTER TABLE campaign_members
    ADD COLUMN hidden_from_public_list BOOLEAN NOT NULL DEFAULT FALSE;
