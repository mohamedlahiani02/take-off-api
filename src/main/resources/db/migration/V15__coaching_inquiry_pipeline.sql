-- Epic H3 — Coaching inquiry pipeline: status, assignment, internal note, outcome.
ALTER TABLE coaching_inquiries
    ADD COLUMN status TEXT NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW','CONTACTED','SCHEDULED','CLOSED')),
    ADD COLUMN assigned_coach_id UUID REFERENCES coaches(id) ON DELETE SET NULL,
    ADD COLUMN admin_note TEXT,
    ADD COLUMN outcome TEXT
        CHECK (outcome IN ('CONVERTED','NO_SHOW','NOT_INTERESTED')),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX idx_coaching_inquiries_status ON coaching_inquiries (status);
