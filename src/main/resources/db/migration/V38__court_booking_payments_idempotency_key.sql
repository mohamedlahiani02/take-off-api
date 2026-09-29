-- Client-supplied idempotency key for guest cash-ins (US: admin double-click /
-- network-retry protection on POST /bookings/{id}/guest-payments).
--
-- Keys are generated client-side per admin action, scoped to the booking the
-- admin is looking at (not globally unique) — two different bookings may
-- legitimately reuse the same UUID by coincidence, and there is no reason to
-- forbid it. So the uniqueness guarantee is per (booking_id, idempotency_key),
-- not global. A replay of the same key on the same booking must resolve to
-- the SAME row rather than insert a duplicate; a fresh key always inserts.
--
-- Nullable: rows written before this migration (if any) have no key and must
-- not be treated as colliding with each other — Postgres unique constraints
-- already allow any number of NULLs, so no partial-index trick is needed.
-- Every row the application writes from here on always supplies a key
-- (AddGuestPaymentRequest.idempotencyKey is bean-validated @NotBlank).
ALTER TABLE court_booking_payments
    ADD COLUMN idempotency_key TEXT;

ALTER TABLE court_booking_payments
    ADD CONSTRAINT uq_court_booking_payments_booking_idem
        UNIQUE (booking_id, idempotency_key);
