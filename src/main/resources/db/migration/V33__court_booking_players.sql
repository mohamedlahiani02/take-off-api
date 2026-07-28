-- V33 — Per-player payment tracking for padel bookings (Epic 1).
-- One booking = one match; up to 4 participant rows, each owning a tranche.
-- Booking-level payment state (UNPAID/PARTIAL/PAID) is derived in the app layer.

CREATE TABLE court_booking_players (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id        UUID        NOT NULL REFERENCES court_bookings(id) ON DELETE CASCADE,
    user_id           UUID        NOT NULL REFERENCES users(id),
    share_dt          NUMERIC(10,3) NOT NULL,
    payment_status    TEXT        NOT NULL DEFAULT 'PENDING'
                                  CHECK (payment_status IN ('PAID','PENDING','COVERED','WAIVED')),
    payment_method    TEXT        CHECK (payment_method IN ('D17','WALLET','CARD','CASH')),
    paid_at           TIMESTAMPTZ,
    no_show           BOOLEAN     NOT NULL DEFAULT FALSE,
    added_by_admin_id UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (booking_id, user_id)
);

CREATE INDEX idx_cbp_booking ON court_booking_players (booking_id);
CREATE INDEX idx_cbp_user_status ON court_booking_players (user_id, payment_status);

-- Backfill: every existing booking with a user gets one participant row mirroring
-- its current payment status, so derived state equals the old booking-level state.
INSERT INTO court_booking_players (booking_id, user_id, share_dt, payment_status, payment_method, paid_at, added_by_admin_id, created_at)
SELECT
    b.id,
    b.user_id,
    b.price_dt,
    CASE b.payment_status
        WHEN 'PAID'     THEN 'PAID'
        WHEN 'REFUNDED' THEN 'WAIVED'
        ELSE 'PENDING'
    END,
    CASE WHEN b.payment_method IN ('D17','WALLET','CARD','CASH') THEN b.payment_method END,
    CASE WHEN b.payment_status = 'PAID' THEN b.updated_at END,
    b.created_by_admin_id,
    b.created_at
FROM court_bookings b
WHERE b.user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM court_booking_players p
      WHERE p.booking_id = b.id AND p.user_id = b.user_id
  );
