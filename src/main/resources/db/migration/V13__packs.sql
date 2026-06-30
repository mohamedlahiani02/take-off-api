-- Epic F — Packs & Plans Management: pack types, user packs, and a credit ledger.

-- ── pack_types ─────────────────────────────────────────────────
-- Padel packs (Pack 10/25/50) and Pilates plans (10-Class, Monthly Unlimited).
CREATE TABLE pack_types (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    activity        TEXT NOT NULL CHECK (activity IN ('PADEL','PILATES')),
    price_dt        NUMERIC(10,3) NOT NULL,
    credit_count    INT,                               -- NULL when unlimited = TRUE
    unlimited       BOOLEAN NOT NULL DEFAULT FALSE,
    validity_months INT NOT NULL DEFAULT 12,
    display_order   INT NOT NULL DEFAULT 0,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed current public packs/plans.
INSERT INTO pack_types (name, activity, price_dt, credit_count, unlimited, validity_months, display_order) VALUES
    ('Pack 10', 'PADEL', 190, 10, FALSE, 6, 1),
    ('Pack 25', 'PADEL', 450, 25, FALSE, 9, 2),
    ('Pack 50', 'PADEL', 800, 50, FALSE, 12, 3),
    ('10-Class Pack', 'PILATES', 300, 10, FALSE, 6, 1),
    ('Monthly Unlimited', 'PILATES', 420, NULL, TRUE, 1, 2);

-- ── user_packs ─────────────────────────────────────────────────
CREATE TABLE user_packs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pack_type_id        UUID NOT NULL REFERENCES pack_types(id) ON DELETE RESTRICT,
    credits_remaining   INT,                           -- NULL when unlimited
    unlimited           BOOLEAN NOT NULL DEFAULT FALSE,
    purchased_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ NOT NULL,
    status              TEXT NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE','EXPIRED','FROZEN','CANCELLED')),
    -- how it was acquired
    source              TEXT NOT NULL DEFAULT 'PURCHASE'
                            CHECK (source IN ('PURCHASE','ADMIN_ASSIGN')),
    assigned_by_admin_id UUID REFERENCES admins(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_packs_user ON user_packs (user_id, status);
CREATE INDEX idx_user_packs_expiry ON user_packs (expires_at) WHERE status = 'ACTIVE';

-- ── pack_credit_ledger ─────────────────────────────────────────
-- Immutable trail of every credit movement (consume on booking, admin add/remove, expiry).
CREATE TABLE pack_credit_ledger (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_pack_id    UUID NOT NULL REFERENCES user_packs(id) ON DELETE CASCADE,
    delta           INT NOT NULL,                      -- -1 on use, +N on admin add
    credits_after   INT,
    reason          TEXT,
    type            TEXT NOT NULL
                        CHECK (type IN ('CONSUME','ADMIN_ADD','ADMIN_REMOVE','REFUND','EXPIRE')),
    ref_type        TEXT,                              -- 'court_booking' / 'class_booking'
    ref_id          TEXT,
    admin_id        UUID REFERENCES admins(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pack_ledger_pack ON pack_credit_ledger (user_pack_id, created_at DESC);
