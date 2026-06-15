-- Take Off API — baseline schema
-- V1: users, refresh_tokens, products, orders, matches, coaching_inquiries

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── users ──────────────────────────────────────────────────────
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL UNIQUE,
    password_hash   TEXT NOT NULL,
    name            TEXT NOT NULL,
    phone           TEXT,
    tracks          TEXT[] NOT NULL DEFAULT '{}',
    role            TEXT NOT NULL DEFAULT 'USER' CHECK (role IN ('USER','ADMIN')),
    wallet_dt       NUMERIC(10,3) NOT NULL DEFAULT 0,
    padel_level     INT NOT NULL DEFAULT 1 CHECK (padel_level BETWEEN 1 AND 7),
    padel_level_self_declared INT CHECK (padel_level_self_declared BETWEEN 1 AND 7),
    points          INT NOT NULL DEFAULT 100,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users (email);

-- ── refresh_tokens ─────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);

-- ── products ───────────────────────────────────────────────────
CREATE TABLE products (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    category    TEXT NOT NULL CHECK (category IN ('RACKETS','ACCESSORIES','PADELWEAR','PILATES','TOWELS','LIFESTYLE')),
    price_dt    NUMERIC(10,3) NOT NULL,
    description TEXT,
    has_sizes   BOOLEAN NOT NULL DEFAULT false,
    stock       INT NOT NULL DEFAULT 0,
    tag         TEXT,
    image_urls  TEXT[] NOT NULL DEFAULT '{}',
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── orders ─────────────────────────────────────────────────────
CREATE TABLE orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_ref       TEXT NOT NULL UNIQUE,
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    status          TEXT NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','CONFIRMED','PREPARING','SHIPPED',
                                          'PICKUP_READY','DELIVERED','PICKED_UP','CANCELLED')),
    delivery_method TEXT NOT NULL CHECK (delivery_method IN ('PICKUP','DELIVER')),
    delivery_address JSONB,
    payment_method  TEXT NOT NULL CHECK (payment_method IN ('COD','D17','WALLET','CARD')),
    total_dt        NUMERIC(10,3) NOT NULL,
    contact         JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_user ON orders (user_id, created_at DESC);
CREATE INDEX idx_order_status ON orders (status);

CREATE TABLE order_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      UUID NOT NULL,
    product_name    TEXT NOT NULL,
    qty             INT NOT NULL CHECK (qty > 0),
    size            TEXT,
    unit_price_dt   NUMERIC(10,3) NOT NULL
);

-- ── matches (ELO ladder) ───────────────────────────────────────
CREATE TABLE matches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    partner_name    TEXT,
    opponent_names  TEXT[] NOT NULL DEFAULT '{}',
    result          TEXT NOT NULL CHECK (result IN ('W','L')),
    score           TEXT,
    opponent_level  NUMERIC(3,1) NOT NULL,
    delta           INT NOT NULL DEFAULT 0,
    points_after    INT NOT NULL DEFAULT 0,
    played_at       DATE NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_match_user ON matches (user_id, played_at DESC);

-- ── coaching inquiries ─────────────────────────────────────────
CREATE TABLE coaching_inquiries (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                TEXT NOT NULL,
    phone               TEXT NOT NULL,
    email               TEXT NOT NULL,
    lesson_types        TEXT[] NOT NULL DEFAULT '{}',
    availability_grid   JSONB NOT NULL DEFAULT '{}',
    level               TEXT,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
