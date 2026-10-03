-- Promoting a waitlisted member used to charge them instantly, with no
-- fresh consent — contradicting the explicit "nothing will be debited"
-- promise shown when they joined the waitlist. A promotion now creates a
-- time-boxed proposal (PROPOSED) the member must explicitly confirm or
-- decline; nothing is charged until they do. DECLINED and EXPIRED are
-- distinct terminal states from CANCELLED so the audit trail can tell
-- "the member said no" apart from "the member never answered" apart from
-- "a confirmed booking was cancelled".
ALTER TABLE class_bookings DROP CONSTRAINT IF EXISTS class_bookings_status_check;
ALTER TABLE class_bookings ADD CONSTRAINT class_bookings_status_check
    CHECK (status IN ('BOOKED','WAITLIST','PROPOSED','DECLINED','EXPIRED',
                       'CANCELLED','ATTENDED','ABSENT','LATE_CANCEL'));

ALTER TABLE class_bookings ADD COLUMN proposal_expires_at TIMESTAMPTZ;

CREATE INDEX idx_class_bookings_proposal_expiry
    ON class_bookings (status, proposal_expires_at)
    WHERE status = 'PROPOSED';
