-- A guest cash-in against a booking, kept separate from CourtBookingPlayer.
--
-- A walk-in guest paying the remaining balance for a shared match is money
-- collected, not an identity: court_booking_players requires either a member
-- (user_id) or a named guest (guest_name), and fabricating fake named players
-- to represent an anonymous cash payment is exactly what this table exists to
-- avoid. coveredSeats reserves the seat count the payment stands for (so those
-- seats stop being offered for booking) without pretending to know who sits
-- in them. No user_id: guests here have no account and nothing here ever
-- touches a wallet — the method column has no WALLET option at all.
CREATE TABLE court_booking_payments (
    id                    UUID PRIMARY KEY,
    booking_id            UUID NOT NULL REFERENCES court_bookings(id),
    amount_dt             NUMERIC(10,3) NOT NULL CHECK (amount_dt > 0),
    method                TEXT NOT NULL CHECK (method IN ('CASH', 'CARD', 'D17')),
    payer_name            TEXT,
    covered_seats         INT NOT NULL DEFAULT 0 CHECK (covered_seats >= 0),
    reference             TEXT,
    collected_by_admin_id UUID NOT NULL REFERENCES admins(id),
    voided                BOOLEAN NOT NULL DEFAULT FALSE,
    voided_by_admin_id    UUID REFERENCES admins(id) ON DELETE SET NULL,
    voided_at             TIMESTAMPTZ,
    void_reason           TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_court_booking_payments_booking ON court_booking_payments (booking_id);
