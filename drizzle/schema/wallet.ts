/**
 * Wallet schema — wallet_entry (immutable ledger)
 * Ref: ARCHITECTURE.md §8 (WalletEntry) and §3.5 (Wallet & payments)
 *
 * The wallet balance is DERIVED from SUM(delta_tnd) — no mutable balance column.
 * Reasons: auditability, no update-vs-insert race, trivial GDPR export.
 * All amounts in bigint millimes.
 */
import {
  pgSchema,
  uuid,
  varchar,
  bigint,
  timestamp,
  index,
} from 'drizzle-orm/pg-core';
import { users } from './identity';

export const walletSchema = pgSchema('wallet');

export const walletEntries = walletSchema.table(
  'entry',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id),
    /**
     * Signed delta in millimes.
     * Positive = credit (top-up, refund).
     * Negative = debit (booking, pack, order, tournament).
     */
    deltaMillimes: bigint('delta_millimes', { mode: 'bigint' }).notNull(),
    currencyCode: varchar('currency_code', { length: 3 }).notNull().default('TND'),
    /**
     * Reason codes (ARCHITECTURE.md §8 WalletEntry):
     * CREDIT_TOPUP | REFUND | DEBIT_BOOKING | DEBIT_PACK | DEBIT_ORDER | DEBIT_TOURNAMENT
     */
    reason: varchar('reason', { length: 30 }).notNull(),
    /** Type of the referenced entity: booking | order | pack | tournament */
    refType: varchar('ref_type', { length: 20 }),
    refId: uuid('ref_id'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    // NOTE: no updatedAt — this table is append-only
  },
  (t) => ({
    /** Fast wallet balance scan per ARCHITECTURE.md §9 */
    userIdx: index('idx_wallet_user').on(t.userId, t.createdAt),
  }),
);

export type WalletEntry = typeof walletEntries.$inferSelect;
export type NewWalletEntry = typeof walletEntries.$inferInsert;
