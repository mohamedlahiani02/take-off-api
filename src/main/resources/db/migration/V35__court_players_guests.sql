-- V35 — Guests on a padel match, and seat integrity.
--
-- A player named on a booking is not necessarily a member. Until now the only
-- way to seat someone without an account was for an admin to create a "ghost"
-- user, which manufactures a real account row for a person who never signed up
-- and pollutes the member base. A participant row can now carry a plain name
-- instead, with user_id left NULL.
--
-- Naming a partner is explicitly NOT a payment: a guest row starts PENDING and
-- owns no wallet, so it can never be debited or counted as paid.

ALTER TABLE court_booking_players
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE court_booking_players
    ADD COLUMN IF NOT EXISTS guest_name TEXT,
    -- Who put this person on the match: the organiser, the player themselves,
    -- or an admin. Kept so administrative edits stay traceable.
    ADD COLUMN IF NOT EXISTS added_by_user_id UUID REFERENCES users(id);

-- A row identifies a person exactly one way: a member, or a named guest.
ALTER TABLE court_booking_players
    ADD CONSTRAINT cbp_member_or_guest
    CHECK (
        (user_id IS NOT NULL AND guest_name IS NULL)
     OR (user_id IS NULL AND guest_name IS NOT NULL AND length(btrim(guest_name)) > 0)
    );

-- The old UNIQUE (booking_id, user_id) stopped duplicate members but, with
-- NULLs now allowed, would no longer constrain guests. Postgres treats NULLs as
-- distinct in a UNIQUE index, so members get a partial unique index instead and
-- guests get one on their trimmed, case-folded name.
ALTER TABLE court_booking_players
    DROP CONSTRAINT IF EXISTS court_booking_players_booking_id_user_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cbp_booking_member
    ON court_booking_players (booking_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cbp_booking_guest
    ON court_booking_players (booking_id, lower(btrim(guest_name)))
    WHERE guest_name IS NOT NULL;
