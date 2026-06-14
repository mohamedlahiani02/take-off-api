/**
 * Orders schema — orders, order_items
 * Ref: ARCHITECTURE.md §8 (Order aggregate) and §11.4 (Checkout)
 */
import {
  pgSchema,
  uuid,
  varchar,
  bigint,
  integer,
  timestamp,
  jsonb,
  index,
} from 'drizzle-orm/pg-core';
import { users } from './identity';
import { productVariants } from './catalog';

export const ordersSchema = pgSchema('orders');

// ── orders ────────────────────────────────────────────────────
export const orders = ordersSchema.table(
  'order',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id),
    /** Contact and delivery stored as JSONB snapshot (immutable at order time) */
    contact: jsonb('contact').$type<Record<string, string>>().notNull(),
    delivery: jsonb('delivery').$type<Record<string, string>>().notNull(),
    /** Subtotal in millimes */
    subtotalMillimes: bigint('subtotal_millimes', { mode: 'bigint' }).notNull(),
    /** Shipping cost in millimes (0 for free shipping) */
    shippingMillimes: bigint('shipping_millimes', { mode: 'bigint' }).notNull().default(0n),
    /** Total (subtotal + shipping) in millimes */
    totalMillimes: bigint('total_millimes', { mode: 'bigint' }).notNull(),
    currencyCode: varchar('currency_code', { length: 3 }).notNull().default('TND'),
    paymentSource: varchar('payment_source', { length: 10 }).notNull(), // WALLET | CARD | MIXED
    status: varchar('status', { length: 25 }).notNull().default('AWAITING_PAYMENT'),
    /** Payment gateway reference */
    paymentRef: varchar('payment_ref', { length: 100 }),
    placedAt: timestamp('placed_at', { withTimezone: true }).notNull().defaultNow(),
    confirmedAt: timestamp('confirmed_at', { withTimezone: true }),
  },
  (t) => ({
    userIdx: index('idx_order_user').on(t.userId, t.placedAt),
    statusIdx: index('idx_order_status').on(t.status),
  }),
);

// ── order_items ───────────────────────────────────────────────
export const orderItems = ordersSchema.table('order_item', {
  id: uuid('id').defaultRandom().primaryKey(),
  orderId: uuid('order_id')
    .notNull()
    .references(() => orders.id, { onDelete: 'cascade' }),
  variantId: uuid('variant_id')
    .notNull()
    .references(() => productVariants.id),
  quantity: integer('quantity').notNull(),
  /** Unit price at time of order (snapshot) in millimes */
  unitPriceMillimes: bigint('unit_price_millimes', { mode: 'bigint' }).notNull(),
});

export type Order = typeof orders.$inferSelect;
export type OrderItem = typeof orderItems.$inferSelect;
