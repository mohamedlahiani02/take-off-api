-- V18: Add timbre fiscal, delivery fee, and discount fields to orders
ALTER TABLE orders
  ADD COLUMN IF NOT EXISTS timbre_fiscal_dt   NUMERIC(10,3) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS delivery_fee_dt    NUMERIC(10,3) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS discount_code      TEXT,
  ADD COLUMN IF NOT EXISTS discount_amount_dt NUMERIC(10,3) NOT NULL DEFAULT 0;
