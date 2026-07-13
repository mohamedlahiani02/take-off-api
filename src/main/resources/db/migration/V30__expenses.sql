CREATE TABLE expenses (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    date        DATE            NOT NULL,
    category    VARCHAR(50)     NOT NULL,
    amount_dt   NUMERIC(10,3)   NOT NULL,
    supplier    VARCHAR(200),
    notes       TEXT,
    created_by  UUID            REFERENCES admins(id),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_expenses_date ON expenses(date);
