-- Epic G — per-size stock. Each product can have variants (e.g. S/M/L), each with its
-- own stock. Products without sizes simply have no variants and use products.stock.
CREATE TABLE product_variants (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id    UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    size          TEXT NOT NULL,
    stock         INT  NOT NULL DEFAULT 0,
    display_order INT  NOT NULL DEFAULT 0
);

CREATE INDEX idx_product_variants_product ON product_variants (product_id);
