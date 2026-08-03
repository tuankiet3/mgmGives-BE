-- =============================================
-- ENUM TYPES
-- =============================================
CREATE TYPE user_status AS ENUM ('ACTIVE', 'INACTIVE', 'BANNED');
CREATE TYPE user_role AS ENUM ('ADMIN', 'USER');
CREATE TYPE token_type AS ENUM('VERIFY_EMAIL', 'RESET_PASSWORD');

-- 1. Table users
CREATE TABLE users
(
    id            BIGSERIAL PRIMARY KEY,
    password_hash VARCHAR(255),
    email         VARCHAR(255) NOT NULL UNIQUE,
    full_name     VARCHAR(255) NOT NULL,
    phone         VARCHAR(255),
    avatar_url    VARCHAR(255),
    role          user_role,
    status        user_status,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);

-- 2. Table refresh_tokens
CREATE TABLE refresh_tokens
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    token_hash VARCHAR(255),
    expires_at TIMESTAMP,
    is_revoked BOOLEAN,
    created_at TIMESTAMP,
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 3. Table user_tokens
CREATE TABLE user_tokens
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    type       token_type,
    token_hash VARCHAR(255),
    expires_at TIMESTAMP,
    used_at    TIMESTAMP,
    created_at TIMESTAMP,
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users (id)
);