-- ============================================================
-- 0000_init.sql — Take Off initial schema migration
-- Hand-authored from drizzle/schema/* (ARCHITECTURE.md §8, §9)
-- All money as bigint millimes · All timestamps as timestamptz
-- One Postgres schema per module (clean boundaries)
-- ============================================================

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ── Schemas ──────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS padel;
CREATE SCHEMA IF NOT EXISTS pilates;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS orders;
CREATE SCHEMA IF NOT EXISTS wallet;
CREATE SCHEMA IF NOT EXISTS ranking;
CREATE SCHEMA IF NOT EXISTS coaching;
CREATE SCHEMA IF NOT EXISTS content;
CREATE SCHEMA IF NOT EXISTS payments;
CREATE SCHEMA IF NOT EXISTS outbox;

-- ============================================================
-- IDENTITY
-- ============================================================

CREATE TABLE identity.users (
  id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email                   VARCHAR(255) NOT NULL UNIQUE,
  password_hash           VARCHAR(255) NOT NULL,
  name                    VARCHAR(100) NOT NULL,
  phone                   VARCHAR(30),
  tracks                  TEXT[]        NOT NULL DEFAULT '{}',
  locale                  VARCHAR(5)    NOT NULL DEFAULT 'fr',
  role                    VARCHAR(20)   NOT NULL DEFAULT 'USER',
  email_verified_at       TIMESTAMPTZ,
  padel_level             INTEGER       NOT NULL DEFAULT 1,
  padel_level_self_declared BOOLEAN     NOT NULL DEFAULT FALSE,
  points                  INTEGER       NOT NULL DEFAULT 0,
  created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  deleted_at              TIMESTAMPTZ
);

CREATE INDEX idx_user_email ON identity.users (email);

CREATE TABLE identity.sessions (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id             UUID NOT NULL REFERENCES identity.users(id) ON DELETE CASCADE,
  token_hash          VARCHAR(255) NOT NULL,
  device_fingerprint  VARCHAR(255),
  expires_at          TIMESTAMPTZ NOT NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_session_user ON identity.sessions (user_id);

CREATE TABLE identity.audit_event (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_id        UUID REFERENCES identity.users(id) ON DELETE SET NULL,
  action          VARCHAR(100) NOT NULL,
  resource_type   VARCHAR(50)  NOT NULL,
  resource_id     UUID,
  payload         JSONB,
  ip              VARCHAR(45),
  user_agent      TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
  -- No updatedAt — append-only
);

CREATE INDEX idx_audit_actor    ON identity.audit_event (actor_id);
CREATE INDEX idx_audit_resource ON identity.audit_event (resource_type, resource_id);

-- ============================================================
-- PADEL
-- ============================================================

CREATE TABLE padel.court (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name             VARCHAR(50)  NOT NULL,
  surface          VARCHAR(30)  NOT NULL DEFAULT 'artificial_grass',
  capacity_shares  INTEGER      NOT NULL DEFAULT 4,
  open_hour        TIME         NOT NULL DEFAULT '08:00',
  close_hour       TIME         NOT NULL DEFAULT '22:00',
  is_active        BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE padel.slot (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  court_id         UUID    NOT NULL REFERENCES padel.court(id),
  date_local       DATE    NOT NULL,
  start_time       TIME    NOT NULL,
  duration_minutes INTEGER NOT NULL DEFAULT 90,
  status           VARCHAR(30) NOT NULL DEFAULT 'OPEN',
  shares_taken     INTEGER NOT NULL DEFAULT 0,
  -- Optimistic concurrency lock (ARCHITECTURE.md §9)
  version          INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT uq_slot_court_date_time UNIQUE (court_id, date_local, start_time)
);

-- Hot calendar read (ARCHITECTURE.md §9)
CREATE INDEX idx_slot_date_court ON padel.slot (date_local, court_id);

CREATE TABLE padel.booking (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slot_id           UUID NOT NULL REFERENCES padel.slot(id),
  user_id           UUID NOT NULL REFERENCES identity.users(id),
  type              VARCHAR(15) NOT NULL,         -- SHARE | FULL_COURT
  price_millimes    BIGINT      NOT NULL,
  currency_code     VARCHAR(3)  NOT NULL DEFAULT 'TND',
  payment_source    VARCHAR(10) NOT NULL,         -- WALLET | CARD | PACK
  status            VARCHAR(15) NOT NULL DEFAULT 'HELD',
  hold_expires_at   TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  cancelled_at      TIMESTAMPTZ
);

-- Prevent double full-court booking on the same slot (ARCHITECTURE.md §9)
CREATE UNIQUE INDEX idx_slot_fullcourt_unique
  ON padel.booking (slot_id)
  WHERE type = 'FULL_COURT' AND status IN ('HELD', 'CONFIRMED');

CREATE INDEX idx_booking_user ON padel.booking (user_id, created_at DESC);

CREATE TABLE padel.pack (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug             VARCHAR(50)  NOT NULL UNIQUE,
  name             VARCHAR(100) NOT NULL,
  matches          INTEGER      NOT NULL,
  price_millimes   BIGINT       NOT NULL,
  validity_days    INTEGER      NOT NULL,
  sport            VARCHAR(10)  NOT NULL DEFAULT 'PADEL',
  is_active        BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Seed pack catalog (ARCHITECTURE.md §3.2)
INSERT INTO padel.pack (slug, name, matches, price_millimes, validity_days) VALUES
  ('drop-in',  'Drop-in (1 séance)',      1,   20000,  30),
  ('pack-10',  'Pack 10 séances',        10,  190000,  90),
  ('pack-25',  'Pack 25 séances',        25,  450000, 180),
  ('pack-50',  'Pack 50 séances',        50,  800000, 365);

CREATE TABLE padel.user_pack (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          UUID    NOT NULL REFERENCES identity.users(id),
  pack_id          UUID    NOT NULL REFERENCES padel.pack(id),
  total_matches    INTEGER NOT NULL,
  remaining        INTEGER NOT NULL,
  purchased_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at       TIMESTAMPTZ NOT NULL
);

-- FIFO pack consumption — oldest non-expired first (ARCHITECTURE.md §9)
CREATE INDEX idx_userpack_remaining ON padel.user_pack (user_id, expires_at)
  WHERE remaining > 0;

CREATE TABLE padel.pack_consumption_line (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id        UUID NOT NULL REFERENCES padel.booking(id),
  user_pack_id      UUID NOT NULL REFERENCES padel.user_pack(id),
  matches_consumed  INTEGER NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE padel.tournament (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name             VARCHAR(150) NOT NULL,
  kind             VARCHAR(20)  NOT NULL,   -- AMERICANO | KNOCKOUT
  starts_at        TIMESTAMPTZ  NOT NULL,
  capacity         INTEGER      NOT NULL,
  price_millimes   BIGINT       NOT NULL,
  status           VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
  prize            VARCHAR(255)
);

CREATE TABLE padel.tournament_registration (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tournament_id   UUID    NOT NULL REFERENCES padel.tournament(id),
  user_id         UUID    NOT NULL REFERENCES identity.users(id),
  paid            BOOLEAN NOT NULL DEFAULT FALSE,
  registered_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_tournament_user UNIQUE (tournament_id, user_id)
);

-- ============================================================
-- PILATES
-- ============================================================

CREATE TABLE pilates.class_definition (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug              VARCHAR(50)  NOT NULL UNIQUE,
  name              VARCHAR(100) NOT NULL,
  level             VARCHAR(30)  NOT NULL,   -- Reformer | Mat | Sculpt | Prenatal | Private
  duration_minutes  INTEGER      NOT NULL DEFAULT 55,
  description       TEXT,
  is_active         BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Seed class definitions
INSERT INTO pilates.class_definition (slug, name, level, duration_minutes) VALUES
  ('reformer',  'Reformer Pilates', 'Reformer',  55),
  ('mat',       'Mat Pilates',      'Mat',        55),
  ('sculpt',    'Pilates Sculpt',   'Sculpt',     45),
  ('prenatal',  'Prénatal Pilates', 'Prenatal',   50),
  ('private',   'Cours Privé',      'Private',    60);

CREATE TABLE pilates.class_session (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  class_def_id    UUID    NOT NULL REFERENCES pilates.class_definition(id),
  instructor_id   UUID    REFERENCES identity.users(id) ON DELETE SET NULL,
  starts_at       TIMESTAMPTZ NOT NULL,
  capacity        INTEGER NOT NULL DEFAULT 8,
  spots_taken     INTEGER NOT NULL DEFAULT 0,
  status          VARCHAR(20) NOT NULL DEFAULT 'OPEN'   -- OPEN | FULL | CANCELLED
);

CREATE INDEX idx_session_starts_at ON pilates.class_session (starts_at);
CREATE INDEX idx_session_class_def ON pilates.class_session (class_def_id);

CREATE TABLE pilates.reservation (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id      UUID NOT NULL REFERENCES pilates.class_session(id),
  user_id         UUID NOT NULL REFERENCES identity.users(id),
  status          VARCHAR(20) NOT NULL DEFAULT 'HELD',  -- HELD | CONFIRMED | WAITLISTED | CANCELLED
  payment_source  VARCHAR(10),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  cancelled_at    TIMESTAMPTZ
);

CREATE INDEX idx_reservation_session_user ON pilates.reservation (session_id, user_id);
CREATE INDEX idx_reservation_user ON pilates.reservation (user_id);

-- ============================================================
-- CATALOG
-- ============================================================

CREATE TABLE catalog.product (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug         VARCHAR(100) NOT NULL UNIQUE,
  category     VARCHAR(30)  NOT NULL,
  name         VARCHAR(150) NOT NULL,
  description  TEXT,
  images       JSONB        NOT NULL DEFAULT '[]',
  brand_own    BOOLEAN      NOT NULL DEFAULT FALSE,
  tags         TEXT[]       NOT NULL DEFAULT '{}',
  is_active    BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_product_category ON catalog.product (category);
-- Full-text search via pg_trgm (ARCHITECTURE.md §9 note)
CREATE INDEX idx_product_search ON catalog.product USING gin(name gin_trgm_ops);

CREATE TABLE catalog.product_variant (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id        UUID        NOT NULL REFERENCES catalog.product(id) ON DELETE CASCADE,
  sku               VARCHAR(50) NOT NULL UNIQUE,
  size              VARCHAR(20),
  price_millimes    BIGINT      NOT NULL,
  currency_code     VARCHAR(3)  NOT NULL DEFAULT 'TND',
  stock             INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX idx_variant_product ON catalog.product_variant (product_id);

-- ============================================================
-- ORDERS
-- ============================================================

CREATE TABLE orders.order (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id              UUID        NOT NULL REFERENCES identity.users(id),
  contact              JSONB       NOT NULL,
  delivery             JSONB       NOT NULL,
  subtotal_millimes    BIGINT      NOT NULL,
  shipping_millimes    BIGINT      NOT NULL DEFAULT 0,
  total_millimes       BIGINT      NOT NULL,
  currency_code        VARCHAR(3)  NOT NULL DEFAULT 'TND',
  payment_source       VARCHAR(10) NOT NULL,
  status               VARCHAR(25) NOT NULL DEFAULT 'AWAITING_PAYMENT',
  payment_ref          VARCHAR(100),
  placed_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  confirmed_at         TIMESTAMPTZ
);

CREATE INDEX idx_order_user   ON orders.order (user_id, placed_at DESC);
CREATE INDEX idx_order_status ON orders.order (status);

CREATE TABLE orders.order_item (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id              UUID    NOT NULL REFERENCES orders.order(id) ON DELETE CASCADE,
  variant_id            UUID    NOT NULL REFERENCES catalog.product_variant(id),
  quantity              INTEGER NOT NULL,
  unit_price_millimes   BIGINT  NOT NULL
);

-- ============================================================
-- WALLET (immutable ledger)
-- ============================================================

CREATE TABLE wallet.entry (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          UUID        NOT NULL REFERENCES identity.users(id),
  delta_millimes   BIGINT      NOT NULL,    -- positive=credit, negative=debit
  currency_code    VARCHAR(3)  NOT NULL DEFAULT 'TND',
  reason           VARCHAR(30) NOT NULL,
  ref_type         VARCHAR(20),
  ref_id           UUID,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
  -- NO updated_at — this table is APPEND-ONLY
);

CREATE INDEX idx_wallet_user ON wallet.entry (user_id, created_at);

-- ============================================================
-- RANKING
-- ============================================================

CREATE TABLE ranking.match (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id             UUID        NOT NULL REFERENCES identity.users(id),
  opponent_text       VARCHAR(255) NOT NULL,
  partner_text        VARCHAR(255),
  result              VARCHAR(1)  NOT NULL,   -- W | L
  score               VARCHAR(50),
  opponent_level_est  INTEGER,
  old_level           INTEGER     NOT NULL,
  new_level           INTEGER     NOT NULL,
  points_delta        INTEGER     NOT NULL,
  played_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  source              VARCHAR(25) NOT NULL DEFAULT 'MANUAL',
  booking_id          UUID
);

CREATE INDEX idx_match_user ON ranking.match (user_id, played_at DESC);

CREATE TABLE ranking.ladder_entry (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID        NOT NULL REFERENCES identity.users(id),
  period      VARCHAR(7)  NOT NULL,   -- YYYY-MM
  points      INTEGER     NOT NULL,
  wins        INTEGER     NOT NULL DEFAULT 0,
  losses      INTEGER     NOT NULL DEFAULT 0,
  rank        INTEGER,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_ladder_user_period UNIQUE (user_id, period)
);

-- Leaderboard query (ARCHITECTURE.md §9)
CREATE INDEX idx_ladder_period_points ON ranking.ladder_entry (period, points DESC);
CREATE INDEX idx_ladder_user_period   ON ranking.ladder_entry (user_id, period);

-- ============================================================
-- COACHING
-- ============================================================

CREATE TABLE coaching.coach (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug          VARCHAR(50)  NOT NULL UNIQUE,
  name          VARCHAR(100) NOT NULL,
  role          VARCHAR(50)  NOT NULL,
  bio           TEXT,
  specialties   TEXT[]       NOT NULL DEFAULT '{}',
  achievements  TEXT[]       NOT NULL DEFAULT '{}',
  photo_url     VARCHAR(500),
  sport         VARCHAR(10)  NOT NULL,   -- PADEL | PILATES
  is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
  user_id       UUID REFERENCES identity.users(id) ON DELETE SET NULL
);

CREATE TABLE coaching.coaching_inquiry (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id               UUID REFERENCES identity.users(id) ON DELETE SET NULL,
  name                  VARCHAR(100) NOT NULL,
  phone                 VARCHAR(30)  NOT NULL,
  email                 VARCHAR(255) NOT NULL,
  course_types          TEXT[]       NOT NULL DEFAULT '{}',
  group_note            TEXT,
  availability_matrix   JSONB,
  niveau                VARCHAR(20),
  other_sports          VARCHAR(255),
  motivation            TEXT,
  language              VARCHAR(5)   NOT NULL DEFAULT 'fr',
  notes                 TEXT,
  status                VARCHAR(15)  NOT NULL DEFAULT 'NEW',
  created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  contacted_at          TIMESTAMPTZ,
  closed_at             TIMESTAMPTZ
);

CREATE INDEX idx_inquiry_status ON coaching.coaching_inquiry (status, created_at);
CREATE INDEX idx_inquiry_email  ON coaching.coaching_inquiry (email);

-- ============================================================
-- CONTENT
-- ============================================================

CREATE TABLE content.partner (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name           VARCHAR(100) NOT NULL,
  logo_url       VARCHAR(500),
  url            VARCHAR(500),
  display_order  INTEGER NOT NULL DEFAULT 0,
  is_active      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE content.faq_item (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  question       TEXT    NOT NULL,
  answer         TEXT    NOT NULL,
  section        VARCHAR(30) NOT NULL DEFAULT 'general',
  display_order  INTEGER NOT NULL DEFAULT 0,
  is_active      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE content.hours (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  sport         VARCHAR(10) NOT NULL,
  day_of_week   INTEGER     NOT NULL,   -- 1=Mon ... 7=Sun
  open_time     VARCHAR(5),             -- HH:MM
  close_time    VARCHAR(5),
  is_closed     BOOLEAN     NOT NULL DEFAULT FALSE,
  note          VARCHAR(255),
  CONSTRAINT uq_hours_sport_day UNIQUE (sport, day_of_week)
);

-- ============================================================
-- PAYMENTS
-- ============================================================

CREATE TABLE payments.payment_intent (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id           UUID REFERENCES identity.users(id) ON DELETE SET NULL,
  amount_millimes   BIGINT      NOT NULL,
  currency_code     VARCHAR(3)  NOT NULL DEFAULT 'TND',
  provider          VARCHAR(20) NOT NULL DEFAULT 'konnect',
  provider_ref      VARCHAR(100),
  redirect_url      VARCHAR(500),
  status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  purpose_type      VARCHAR(20) NOT NULL,
  purpose_id        UUID,
  webhook_payload   JSONB,
  idempotency_key   VARCHAR(100),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  resolved_at       TIMESTAMPTZ
);

CREATE INDEX idx_payment_intent_status      ON payments.payment_intent (status, created_at);
CREATE INDEX idx_payment_intent_user        ON payments.payment_intent (user_id);
CREATE INDEX idx_payment_intent_provider_ref ON payments.payment_intent (provider_ref);

-- ============================================================
-- OUTBOX (transactional outbox pattern)
-- ============================================================

CREATE TABLE outbox.outbox_event (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  aggregate_type  VARCHAR(50)  NOT NULL,
  aggregate_id    UUID         NOT NULL,
  event_type      VARCHAR(100) NOT NULL,
  payload         JSONB        NOT NULL,
  published_at    TIMESTAMPTZ,
  attempts        INTEGER      NOT NULL DEFAULT 0,
  last_attempt_at TIMESTAMPTZ,
  last_error      VARCHAR(500),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
  -- No updatedAt — published_at is set once when delivered
);

-- Partial index for fast poll of unpublished events (ARCHITECTURE.md §9)
CREATE INDEX idx_outbox_unpublished ON outbox.outbox_event (created_at)
  WHERE published_at IS NULL;

CREATE INDEX idx_outbox_aggregate ON outbox.outbox_event (aggregate_type, aggregate_id);
