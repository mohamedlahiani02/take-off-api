CREATE TYPE admin_role AS ENUM ('SUPER_ADMIN', 'MANAGER', 'RECEPTION', 'COACH');

CREATE TABLE admins (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT        NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    name          TEXT        NOT NULL,
    role          admin_role  NOT NULL DEFAULT 'RECEPTION',
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id     UUID        NOT NULL REFERENCES admins(id),
    action       TEXT        NOT NULL,
    target_type  TEXT,
    target_id    TEXT,
    payload      JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
