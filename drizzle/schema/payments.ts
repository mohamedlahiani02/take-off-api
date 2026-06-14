/**
 * Payments schema — payment_intents
 * Ref: ARCHITECTURE.md §13 (Payments — provider abstraction)
 */
import {
  pgSchema,
  uuid,
  varchar,
  bigint,
  timestamp,
  jsonb,
  index,
} from 'drizzle-orm/pg-core';
import { users } from './identity';

export const paymentsSchema = pgSchema('payments');

export const paymentIntents = paymentsSchema.table(
  'payment_intent',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id').references(() => users.id, { onDelete: 'set null' }),
    /** amountMillimes: bigint — ARCHITECTURE.md §9 (money as bigint) */
    amountMillimes: bigint('amount_millimes', { mode: 'bigint' }).notNull(),
    currencyCode: varchar('currency_code', { length: 3 }).notNull().default('TND'),
    provider: varchar('provider', { length: 20 }).notNull().default('konnect'),
    /** Provider-assigned reference */
    providerRef: varchar('provider_ref', { length: 100 }),
    /** URL to redirect the user to provider's payment surface */
    redirectUrl: varchar('redirect_url', { length: 500 }),
    status: varchar('status', { length: 20 }).notNull().default('PENDING'),
    // PENDING | COMPLETED | FAILED | CANCELLED | EXPIRED
    /** What this payment intent is for */
    purposeType: varchar('purpose_type', { length: 20 }).notNull(),
    // booking | order | pack | wallet_topup | tournament
    purposeId: uuid('purpose_id'),
    /** Raw webhook payload stored for debugging */
    webhookPayload: jsonb('webhook_payload'),
    idempotencyKey: varchar('idempotency_key', { length: 100 }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    resolvedAt: timestamp('resolved_at', { withTimezone: true }),
  },
  (t) => ({
    statusCreatedIdx: index('idx_payment_intent_status').on(t.status, t.createdAt),
    userIdx: index('idx_payment_intent_user').on(t.userId),
    providerRefIdx: index('idx_payment_intent_provider_ref').on(t.providerRef),
  }),
);

export type PaymentIntent = typeof paymentIntents.$inferSelect;
