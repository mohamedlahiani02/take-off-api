-- Epic B — User Management: ghost users, account status, admin authorship, wallet ledger.

-- Account lifecycle status. ACTIVE = normal; GHOST = created by admin, no password yet;
-- BLOCKED = login prevented; DELETED = soft-deleted (GDPR).
ALTER TABLE users
    ADD COLUMN account_status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (account_status IN ('ACTIVE','GHOST','BLOCKED','DELETED'));

-- Which admin created this user (ghost-user creation at reception).
ALTER TABLE users
    ADD COLUMN created_by_admin_id UUID REFERENCES admins(id) ON DELETE SET NULL;

-- Ghost users have no password until they set one via invite, so allow NULL.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- (phone already has a unique index from V4)
CREATE INDEX idx_users_account_status ON users (account_status);

-- ── wallet ledger ──────────────────────────────────────────────
-- Every wallet movement (manual admin credit/debit, refund, top-up, payment).
-- Running balance lives on users.wallet_dt; this is the immutable audit trail.
CREATE TABLE wallet_ledger (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount_dt       NUMERIC(10,3) NOT NULL,            -- positive = credit, negative = debit
    balance_after   NUMERIC(10,3) NOT NULL,
    type            TEXT NOT NULL
                        CHECK (type IN ('TOPUP','ADMIN_CREDIT','ADMIN_DEBIT','REFUND','PAYMENT')),
    reason          TEXT,                              -- mandatory for ADMIN_CREDIT / ADMIN_DEBIT
    admin_id        UUID REFERENCES admins(id) ON DELETE SET NULL,
    ref_type        TEXT,                              -- e.g. 'order','court_booking','tournament'
    ref_id          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_wallet_ledger_user ON wallet_ledger (user_id, created_at DESC);
