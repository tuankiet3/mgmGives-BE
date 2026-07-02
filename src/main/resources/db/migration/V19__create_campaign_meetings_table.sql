CREATE TYPE campaign_meeting_status AS ENUM ('SCHEDULED', 'CANCELLED', 'ENDED');

CREATE TABLE campaign_meetings (
    id               BIGSERIAL PRIMARY KEY,
    campaign_id      BIGINT NOT NULL,
    created_by       BIGINT NOT NULL,
    webex_meeting_id VARCHAR(255),
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    meeting_url      TEXT NOT NULL,
    notify_all       BOOLEAN NOT NULL DEFAULT TRUE,
    invited_user_ids TEXT,
    invited_count    INTEGER,
    invitations_sent_at TIMESTAMP,
    start_time       TIMESTAMP NOT NULL,
    end_time         TIMESTAMP NOT NULL,
    status           campaign_meeting_status NOT NULL DEFAULT 'SCHEDULED',
    notes            TEXT,
    notes_updated_at TIMESTAMP,
    notes_updated_by BIGINT,
    updated_by       BIGINT,
    cancelled_at     TIMESTAMP,
    cancelled_by     BIGINT,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    CONSTRAINT fk_campaign_meeting_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT fk_campaign_meeting_creator  FOREIGN KEY (created_by)  REFERENCES users (id),
    CONSTRAINT fk_campaign_meeting_notes_updated_by FOREIGN KEY (notes_updated_by) REFERENCES users (id),
    CONSTRAINT fk_campaign_meeting_updated_by FOREIGN KEY (updated_by) REFERENCES users (id),
    CONSTRAINT fk_campaign_meeting_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES users (id),
    CONSTRAINT chk_campaign_meeting_time CHECK (end_time > start_time)
);

CREATE INDEX idx_campaign_meetings_campaign_id ON campaign_meetings (campaign_id);
CREATE INDEX idx_campaign_meetings_status ON campaign_meetings (status);
CREATE INDEX idx_campaign_meetings_start_time ON campaign_meetings (start_time);

ALTER TABLE campaign_medias
    ADD COLUMN meeting_id BIGINT,
    ADD CONSTRAINT fk_campaign_media_meeting FOREIGN KEY (meeting_id) REFERENCES campaign_meetings (id);

CREATE INDEX idx_campaign_medias_meeting_id ON campaign_medias (meeting_id);

CREATE TABLE user_webex_connections (
     id                       BIGSERIAL PRIMARY KEY,
     user_id                  BIGINT NOT NULL UNIQUE,
     webex_person_id          VARCHAR(255),
     webex_email              VARCHAR(255),
     access_token             TEXT NOT NULL,
     refresh_token            TEXT NOT NULL,
     access_token_expires_at  TIMESTAMP,
     refresh_token_expires_at TIMESTAMP,
     connected_at             TIMESTAMP,
     updated_at               TIMESTAMP,
     created_at               TIMESTAMP,
     CONSTRAINT fk_user_webex_connection_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE webex_oauth_states (
    id         BIGSERIAL PRIMARY KEY,
    state      VARCHAR(255) NOT NULL UNIQUE,
    user_id    BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP,
    CONSTRAINT fk_webex_oauth_state_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_user_webex_connections_user_id ON user_webex_connections (user_id);
CREATE INDEX idx_webex_oauth_states_state ON webex_oauth_states (state);
CREATE INDEX idx_webex_oauth_states_user_id ON webex_oauth_states (user_id);
