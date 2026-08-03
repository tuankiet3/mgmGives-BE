CREATE TYPE campaign_member_role_new AS ENUM ('CAMPAIGN_ADMIN', 'VOLUNTEER');
ALTER TABLE campaign_members
ALTER COLUMN role_in_campaign TYPE campaign_member_role_new
    USING role_in_campaign::text::campaign_member_role_new;

DROP TYPE campaign_member_role;
ALTER TYPE campaign_member_role_new RENAME TO campaign_member_role;

CREATE TABLE campaign_followers (
      id           BIGSERIAL PRIMARY KEY,
      campaign_id  BIGINT NOT NULL,
      user_id      BIGINT NOT NULL,
      followed_at  TIMESTAMP NOT NULL DEFAULT now(),
      CONSTRAINT fk_follower_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
      CONSTRAINT fk_follower_user     FOREIGN KEY (user_id)     REFERENCES users (id),
      CONSTRAINT uq_follower_campaign_user UNIQUE (campaign_id, user_id)
);

CREATE INDEX idx_campaign_followers_user_id ON campaign_followers (user_id);
CREATE INDEX idx_campaign_followers_campaign_id ON campaign_followers (campaign_id);