-- A waitlisted class booking charges nothing (no pack credit consumed, no
-- wallet debited) and must say so honestly rather than reusing SINGLE, which
-- claimed a real per-session charge that never happened.
ALTER TABLE class_bookings DROP CONSTRAINT IF EXISTS class_bookings_paid_with_check;
ALTER TABLE class_bookings ADD CONSTRAINT class_bookings_paid_with_check
    CHECK (paid_with IN ('SINGLE','PACK','UNLIMITED','COMP','WAITLIST'));
