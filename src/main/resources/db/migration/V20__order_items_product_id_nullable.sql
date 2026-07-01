-- order_items.product_id must allow NULL for store items that do not yet have
-- a corresponding DB product record (hardcoded catalogue, custom line items, etc.)
ALTER TABLE order_items ALTER COLUMN product_id DROP NOT NULL;
