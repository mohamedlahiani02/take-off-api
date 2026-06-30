-- Epic E — Pilates Schedule & Class Management: class types, sessions, bookings.

-- ── class_types ────────────────────────────────────────────────
-- Reformer Flow, Mat Pilates, etc. (also feeds Pilates page content, I-08).
CREATE TABLE class_types (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    level           TEXT,                  -- 'Beginner' / 'All levels' / 'Advanced'
    duration_min    INT NOT NULL DEFAULT 50,
    description     TEXT,
    photo_url       TEXT,
    default_price_dt NUMERIC(10,3) NOT NULL DEFAULT 35,
    display_order   INT NOT NULL DEFAULT 0,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── class_sessions ─────────────────────────────────────────────
-- A concrete scheduled instance taught by an instructor (a coach row, activity=PILATES).
CREATE TABLE class_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_type_id       UUID NOT NULL REFERENCES class_types(id) ON DELETE RESTRICT,
    instructor_id       UUID REFERENCES coaches(id) ON DELETE SET NULL,
    starts_at           TIMESTAMPTZ NOT NULL,
    duration_min        INT NOT NULL DEFAULT 50,
    max_spots           INT NOT NULL DEFAULT 8,
    price_dt            NUMERIC(10,3) NOT NULL DEFAULT 35,
    status              TEXT NOT NULL DEFAULT 'SCHEDULED'
                            CHECK (status IN ('SCHEDULED','CANCELLED','COMPLETED')),
    created_by_admin_id UUID REFERENCES admins(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_class_sessions_time ON class_sessions (starts_at);
CREATE INDEX idx_class_sessions_instructor ON class_sessions (instructor_id, starts_at);

-- ── class_bookings ─────────────────────────────────────────────
CREATE TABLE class_bookings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID NOT NULL REFERENCES class_sessions(id) ON DELETE CASCADE,
    user_id             UUID REFERENCES users(id) ON DELETE SET NULL,
    status              TEXT NOT NULL DEFAULT 'BOOKED'
                            CHECK (status IN ('BOOKED','WAITLIST','CANCELLED',
                                              'ATTENDED','ABSENT','LATE_CANCEL')),
    -- credit source: how this seat was paid for
    paid_with           TEXT NOT NULL DEFAULT 'SINGLE'
                            CHECK (paid_with IN ('SINGLE','PACK','UNLIMITED','COMP')),
    user_pack_id        UUID,
    price_dt            NUMERIC(10,3) NOT NULL DEFAULT 0,
    created_by_admin_id UUID REFERENCES admins(id) ON DELETE SET NULL,
    waitlist_position   INT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_class_bookings_session ON class_bookings (session_id, status);
CREATE INDEX idx_class_bookings_user ON class_bookings (user_id, created_at DESC);
