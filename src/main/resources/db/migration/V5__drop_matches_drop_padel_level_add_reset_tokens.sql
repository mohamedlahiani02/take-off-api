DROP TABLE IF EXISTS matches;

ALTER TABLE users DROP COLUMN IF EXISTS padel_level;
ALTER TABLE users DROP COLUMN IF EXISTS padel_level_self_declared;

-- Wallet is now points-based (1 pt = 1 TND). Reset default to 0.
ALTER TABLE users ALTER COLUMN points SET DEFAULT 0;

CREATE TABLE password_reset_tokens (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   TEXT        NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    used         BOOLEAN     NOT NULL DEFAULT FALSE
);
