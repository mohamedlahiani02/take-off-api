CREATE TABLE payment_intents (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        REFERENCES users(id),
    ref_type        VARCHAR(30) NOT NULL,
    ref_id          VARCHAR(100) NOT NULL,
    amount_dt       NUMERIC(10,3) NOT NULL,
    konnect_pay_ref VARCHAR(200),
    konnect_pay_url TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);
CREATE INDEX ON payment_intents(konnect_pay_ref) WHERE konnect_pay_ref IS NOT NULL;
CREATE INDEX ON payment_intents(ref_type, ref_id);
