-- Epic D — Tournament Management: tournaments, pricing tiers, promo codes,
-- custom form fields, and registrations.

-- ── tournaments ────────────────────────────────────────────────
CREATE TABLE tournaments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title               TEXT NOT NULL,
    description         TEXT,
    banner_url          TEXT,
    format              TEXT NOT NULL
                            CHECK (format IN ('AMERICANO','KNOCKOUT','ROUND_ROBIN','SWISS')),
    category            TEXT NOT NULL
                            CHECK (category IN ('MIXED','MEN','WOMEN','JUNIOR','SENIOR')),
    starts_at           TIMESTAMPTZ NOT NULL,
    ends_at             TIMESTAMPTZ,
    entry_fee_dt        NUMERIC(10,3) NOT NULL DEFAULT 0,
    prize               TEXT,
    status              TEXT NOT NULL DEFAULT 'DRAFT'
                            CHECK (status IN ('DRAFT','PUBLISHED','REGISTRATION_OPEN',
                                              'REGISTRATION_CLOSED','ONGOING','FINISHED')),
    max_participants    INT,
    registration_deadline TIMESTAMPTZ,
    registration_mode   TEXT NOT NULL DEFAULT 'OPEN'
                            CHECK (registration_mode IN ('OPEN','MEMBERS_ONLY','INVITATION_ONLY')),
    auto_waitlist       BOOLEAN NOT NULL DEFAULT FALSE,
    manual_validation   BOOLEAN NOT NULL DEFAULT FALSE,
    payment_rule        TEXT NOT NULL DEFAULT 'BOTH'
                            CHECK (payment_rule IN ('ONLINE','AT_CLUB','BOTH')),
    created_by_admin_id UUID REFERENCES admins(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tournaments_status ON tournaments (status, starts_at);

-- ── tournament_pricing ─────────────────────────────────────────
-- Pricing tiers by category label (D-12), e.g. Junior 40 / Adult 80.
CREATE TABLE tournament_pricing (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id   UUID NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    label           TEXT NOT NULL,
    price_dt        NUMERIC(10,3) NOT NULL,
    display_order   INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_tournament_pricing_t ON tournament_pricing (tournament_id);

-- ── tournament_promo_codes ─────────────────────────────────────
CREATE TABLE tournament_promo_codes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id   UUID NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    code            TEXT NOT NULL,
    discount_type   TEXT NOT NULL CHECK (discount_type IN ('FLAT','PERCENT')),
    discount_value  NUMERIC(10,3) NOT NULL,
    max_uses        INT,
    uses            INT NOT NULL DEFAULT 0,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tournament_id, code)
);

-- ── tournament_fields ──────────────────────────────────────────
-- Custom registration form builder (D2). Each row is one ordered, typed field.
CREATE TABLE tournament_fields (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id   UUID NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    field_type      TEXT NOT NULL
                        CHECK (field_type IN ('SHORT_TEXT','LONG_TEXT','DROPDOWN','MULTI_SELECT',
                                              'DATE','PARTNER','FILE','TSHIRT_SIZE','PHONE',
                                              'AGREEMENT')),
    label           TEXT NOT NULL,
    help_text       TEXT,
    required        BOOLEAN NOT NULL DEFAULT FALSE,
    options         JSONB,        -- choices for DROPDOWN/MULTI_SELECT; constraints for FILE
    display_order   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tournament_fields_t ON tournament_fields (tournament_id, display_order);

-- ── tournament_registrations ───────────────────────────────────
CREATE TABLE tournament_registrations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id       UUID NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    user_id             UUID REFERENCES users(id) ON DELETE SET NULL,
    category_label      TEXT,
    answers             JSONB NOT NULL DEFAULT '{}',   -- keyed by tournament_fields.id
    payment_status      TEXT NOT NULL DEFAULT 'PENDING'
                            CHECK (payment_status IN ('PAID','PAY_AT_CLUB','PENDING','REFUNDED')),
    status              TEXT NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('CONFIRMED','PENDING','WAITLIST','REJECTED','CANCELLED')),
    promo_code_id       UUID REFERENCES tournament_promo_codes(id) ON DELETE SET NULL,
    amount_paid_dt      NUMERIC(10,3) NOT NULL DEFAULT 0,
    created_by_admin_id UUID REFERENCES admins(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tournament_regs_t ON tournament_registrations (tournament_id, status);
CREATE INDEX idx_tournament_regs_user ON tournament_registrations (user_id);
