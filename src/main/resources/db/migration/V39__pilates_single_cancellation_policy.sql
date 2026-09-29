-- Club policy for Pilates SINGLE (wallet-paid, no-pack) class bookings:
--
-- 1) A member who cancels their own booking OUTSIDE the late-cancellation
--    window does not get their DT back in the wallet. Instead they get a
--    non-monetary use credit, valid for a future booking. Rather than invent
--    a third parallel payment/credit mechanism, this credit is modelled as a
--    hidden one-session PACK credit (see MemberClassController.cancel), so it
--    is consumed through book()'s existing PACK branch unchanged. This row is
--    that hidden pack type: PILATES activity so it matches book()'s pack
--    lookup, free (price 0), never sold (active = FALSE keeps it out of the
--    public /classes/packs catalogue).
INSERT INTO pack_types (id, name, activity, price_dt, credit_count, unlimited, validity_months, display_order, active)
VALUES (
    '00000000-0000-0000-0000-0000000000c1',
    'Crédit d''utilisation (annulation)',
    'PILATES', 0, 1, FALSE, 6, 9999, FALSE
)
ON CONFLICT (id) DO NOTHING;

-- 2) When the CLUB cancels an entire session, each SINGLE-paid booking on it
--    is automatically refunded to the wallet when the cancellation happens
--    more than 24h before the session's start. Inside 24h, no automatic
--    refund happens; the booking is flagged here so an admin can settle it
--    manually later (mirrors the read-only unpaid-legacy diagnostic — no
--    refund endpoint is added yet, just a reachable list).
ALTER TABLE class_bookings ADD COLUMN IF NOT EXISTS refund_pending BOOLEAN NOT NULL DEFAULT FALSE;
