/**
 * Catalog schema — products, product_variants
 * Ref: ARCHITECTURE.md §8 (Product, ProductVariant) and §3.4 (Store)
 */
import {
  pgSchema,
  uuid,
  varchar,
  text,
  boolean,
  bigint,
  integer,
  jsonb,
  index,
} from 'drizzle-orm/pg-core';

export const catalogSchema = pgSchema('catalog');

// ── products ──────────────────────────────────────────────────
export const products = catalogSchema.table(
  'product',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    slug: varchar('slug', { length: 100 }).notNull().unique(),
    /** rackets | accessories | padelwear | towels | pilates | lifestyle */
    category: varchar('category', { length: 30 }).notNull(),
    name: varchar('name', { length: 150 }).notNull(),
    description: text('description'),
    /** Array of image URLs (stored as JSONB for simplicity) */
    images: jsonb('images').$type<string[]>().notNull().default([]),
    brandOwn: boolean('brand_own').notNull().default(false),
    /** Searchable tags (pg_trgm index for full-text) */
    tags: text('tags').array().notNull().default([]),
    isActive: boolean('is_active').notNull().default(true),
  },
  (t) => ({
    categoryIdx: index('idx_product_category').on(t.category),
    // Full-text search via pg_trgm — raw index in 0000_init.sql:
    // CREATE INDEX idx_product_search ON catalog.product USING gin(name gin_trgm_ops)
  }),
);

// ── product_variants ──────────────────────────────────────────
export const productVariants = catalogSchema.table(
  'product_variant',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    productId: uuid('product_id')
      .notNull()
      .references(() => products.id, { onDelete: 'cascade' }),
    sku: varchar('sku', { length: 50 }).notNull().unique(),
    size: varchar('size', { length: 20 }),
    /** Price in millimes (bigint — ARCHITECTURE.md §9) */
    priceMillimes: bigint('price_millimes', { mode: 'bigint' }).notNull(),
    currencyCode: varchar('currency_code', { length: 3 }).notNull().default('TND'),
    stock: integer('stock').notNull().default(0),
  },
  (t) => ({
    productIdx: index('idx_variant_product').on(t.productId),
  }),
);

export type Product = typeof products.$inferSelect;
export type ProductVariant = typeof productVariants.$inferSelect;
