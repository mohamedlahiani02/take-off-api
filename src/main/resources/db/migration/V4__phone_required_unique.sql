-- V4: phone becomes a required, unique login identifier.
-- Pre-launch: existing test rows may have NULL phone. Backfill a non-dialable
-- sentinel so the migration never fails; those accounts must re-register with a
-- real phone number (the sentinel is not a valid +216 number, so login by phone
-- will never match it).
UPDATE users SET phone = '+216-LEGACY-' || left(id::text, 8) WHERE phone IS NULL;

ALTER TABLE users ALTER COLUMN phone SET NOT NULL;

CREATE UNIQUE INDEX idx_users_phone ON users (phone);
