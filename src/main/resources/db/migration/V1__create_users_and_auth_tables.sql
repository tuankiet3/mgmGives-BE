-- 1. Table Role
CREATE TABLE role (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- 2. Table User
CREATE TABLE "user" (
    id            SERIAL PRIMARY KEY,
    username      VARCHAR(255),
    password_hash VARCHAR(255),
    email         VARCHAR(255),
    full_name     VARCHAR(255),
    phone         VARCHAR(255),
    avatar_url    VARCHAR(255),
    status        VARCHAR(50),  -- Enum: ACTIVE, INACTIVE, BANNED
    role_id       INT,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES role (id)
);

-- 3. Table Refresh Token
CREATE TABLE refresh_token (
    id         SERIAL PRIMARY KEY,
    user_id    INT,
    token_hash VARCHAR(255),
    expires_at TIMESTAMP,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP,
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES "user" (id)
);

-- 4. Table Password Reset Token
CREATE TABLE password_reset_token (
    id         SERIAL PRIMARY KEY,
    user_id    INT,
    token_hash VARCHAR(255),
    expires_at TIMESTAMP,
    used_at    TIMESTAMP,
    created_at TIMESTAMP,
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES "user" (id)
);