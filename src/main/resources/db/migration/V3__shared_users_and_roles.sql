CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    token_version BIGINT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_email_lowercase CHECK (email = LOWER(email)),
    CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'MANAGER', 'SELLER', 'USER')),
    CONSTRAINT ck_app_users_token_version CHECK (token_version >= 0)
);

-- Keep seller IDs and password hashes so existing accounts and inventory survive.
INSERT INTO app_users (id, email, password_hash, role, enabled, token_version, created_at)
SELECT id, email, password_hash, 'SELLER', TRUE, 0, created_at FROM sellers;

ALTER TABLE sellers ADD CONSTRAINT fk_seller_user
    FOREIGN KEY (id) REFERENCES app_users (id) ON DELETE CASCADE;
ALTER TABLE sellers DROP COLUMN email;
ALTER TABLE sellers DROP COLUMN password_hash;
ALTER TABLE sellers DROP COLUMN created_at;
