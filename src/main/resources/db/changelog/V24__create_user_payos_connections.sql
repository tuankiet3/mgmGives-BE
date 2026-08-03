-- Create user_payos_connections table to persist custom PayOS credentials for campaign creators/admins
CREATE TABLE user_payos_connections (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL UNIQUE,
    client_id    VARCHAR(255) NOT NULL,
    api_key      VARCHAR(255) NOT NULL,
    checksum_key VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    CONSTRAINT fk_user_payos_connection_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_user_payos_connections_user_id ON user_payos_connections (user_id);
