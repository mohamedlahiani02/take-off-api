-- Add PARTIAL and UNPAID to the court_bookings payment_status check constraint
ALTER TABLE court_bookings DROP CONSTRAINT IF EXISTS court_bookings_payment_status_check;
ALTER TABLE court_bookings ADD CONSTRAINT court_bookings_payment_status_check
    CHECK (payment_status IN ('PAID','PAY_AT_CLUB','PENDING','REFUNDED','PARTIAL','UNPAID'));
