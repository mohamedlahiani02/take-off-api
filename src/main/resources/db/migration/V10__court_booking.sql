-- Epic C — Court Booking Management: courts, bookings (book on behalf), and blocks.

-- ── courts ─────────────────────────────────────────────────────
-- Replaces the previously-static 2 courts with real rows.
CREATE TABLE courts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          TEXT NOT NULL,
    activity      TEXT NOT NULL DEFAULT 'PADEL' CHECK (activity IN ('PADEL','PILATES')),
    display_order INT  NOT NULL DEFAULT 0,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO courts (name, display_order) VALUES
    ('Court 1', 1),
    ('Court 2', 2);

-- ── court_bookings ─────────────────────────────────────────────
CREATE TABLE court_bookings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id            UUID NOT NULL REFERENCES courts(id) ON DELETE RESTRICT,
    user_id             UUID REFERENCES users(id) ON DELETE SET NULL,
    starts_at           TIMESTAMPTZ NOT NULL,
    ends_at             TIMESTAMPTZ NOT NULL,
    mode                TEXT NOT NULL CHECK (mode IN ('SHARE','FULL')),
    price_dt            NUMERIC(10,3) NOT NULL,
    payment_status      TEXT NOT NULL DEFAULT 'PENDING'
                            CHECK (payment_status IN ('PAID','PAY_AT_CLUB','PENDING','REFUNDED')),
    payment_method      TEXT CHECK (payment_method IN ('D17','WALLET','CARD','CASH','PAY_AT_CLUB')),
    status              TEXT NOT NULL DEFAULT 'CONFIRMED'
                            CHECK (status IN ('CONFIRMED','CANCELLED','COMPLETED','NO_SHOW')),
    -- if booked via an active pack, the pack credit that paid for it
    user_pack_id        UUID,
    created_by_admin_id UUID REFERENCES admins(id) ON DELETE SET NULL,
    cancel_reason       TEXT,
    cancelled_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_court_bookings_court_time ON court_bookings (court_id, starts_at);
CREATE INDEX idx_court_bookings_user ON court_bookings (user_id, starts_at DESC);
CREATE INDEX idx_court_bookings_status ON court_bookings (status);

-- ── court_blocks ───────────────────────────────────────────────
-- Maintenance / private events / recurring club training. Shown as unavailable publicly.
CREATE TABLE court_blocks (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id            UUID NOT NULL REFERENCES courts(id) ON DELETE CASCADE,
    starts_at           TIMESTAMPTZ NOT NULL,
    ends_at             TIMESTAMPTZ NOT NULL,
    reason              TEXT NOT NULL,
    -- recurrence: NULL = one-off; otherwise weekly on this day-of-week (0=Sun..6=Sat)
    recurring_dow       INT CHECK (recurring_dow BETWEEN 0 AND 6),
    recurring_until     DATE,
    created_by_admin_id UUID REFERENCES admins(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_court_blocks_court_time ON court_blocks (court_id, starts_at);
