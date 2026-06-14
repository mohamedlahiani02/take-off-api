/**
 * Padel schema — courts, slots, bookings, pack catalog, user_packs, pack_consumption_lines
 * Ref: ARCHITECTURE.md §8 (Padel aggregates) and §9 (DB design / critical indexes)
 */
import {
  pgSchema,
  uuid,
  varchar,
  integer,
  bigint,
  boolean,
  timestamp,
  date,
  time,
  index,
  uniqueIndex,
} from 'drizzle-orm/pg-core';
import { users } from './identity';

export const padelSchema = pgSchema('padel');

// ── courts ────────────────────────────────────────────────────
export const courts = padelSchema.table('court', {
  id: uuid('id').defaultRandom().primaryKey(),
  name: varchar('name', { length: 50 }).notNull(),
  surface: varchar('surface', { length: 30 }).notNull().default('artificial_grass'),
  /** Number of share positions (always 4 per ARCHITECTURE.md §8) */
  capacityShares: integer('capacity_shares').notNull().default(4),
  openHour: time('open_hour').notNull().default('08:00'),
  closeHour: time('close_hour').notNull().default('22:00'),
  isActive: boolean('is_active').notNull().default(true),
});

// ── slots (pre-generated 16-day rolling window) ───────────────
export const slots = padelSchema.table(
  'slot',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    courtId: uuid('court_id')
      .notNull()
      .references(() => courts.id),
    /** Local date (Africa/Tunis) stored as date — no TZ offset */
    dateLocal: date('date_local').notNull(),
    startTime: time('start_time').notNull(),
    durationMinutes: integer('duration_minutes').notNull().default(90),
    status: varchar('status', { length: 30 }).notNull().default('OPEN'),
    /** 0–4 share positions taken */
    sharesTaken: integer('shares_taken').notNull().default(0),
    /** Optimistic concurrency version — incremented on every booking/cancel */
    version: integer('version').notNull().default(0),
  },
  (t) => ({
    /** Hot read: calendar query filtered by date + court (ARCHITECTURE.md §9) */
    dateCourtIdx: index('idx_slot_date_court').on(t.dateLocal, t.courtId),
  }),
);

// ── bookings ──────────────────────────────────────────────────
export const bookings = padelSchema.table(
  'booking',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    slotId: uuid('slot_id')
      .notNull()
      .references(() => slots.id),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id),
    type: varchar('type', { length: 15 }).notNull(), // SHARE | FULL_COURT
    /** Price paid in millimes (bigint — ARCHITECTURE.md §9) */
    priceMillimes: bigint('price_millimes', { mode: 'bigint' }).notNull(),
    currencyCode: varchar('currency_code', { length: 3 }).notNull().default('TND'),
    paymentSource: varchar('payment_source', { length: 10 }).notNull(), // WALLET | CARD | PACK
    status: varchar('status', { length: 15 }).notNull().default('HELD'), // HELD | CONFIRMED | CANCELLED
    holdExpiresAt: timestamp('hold_expires_at', { withTimezone: true }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    cancelledAt: timestamp('cancelled_at', { withTimezone: true }),
  },
  (t) => ({
    /** Prevent double full-court booking on the same slot (ARCHITECTURE.md §9) */
    fullCourtUniqueIdx: uniqueIndex('idx_slot_fullcourt_unique')
      .on(t.slotId)
      .where(
        // NOTE: drizzle-kit may not yet support partial WHERE in uniqueIndex;
        // the raw SQL equivalent is in 0000_init.sql
      ),
    userIdx: index('idx_booking_user').on(t.userId, t.createdAt),
  }),
);

// ── pack catalog ──────────────────────────────────────────────
export const packs = padelSchema.table('pack', {
  id: uuid('id').defaultRandom().primaryKey(),
  slug: varchar('slug', { length: 50 }).notNull().unique(),
  name: varchar('name', { length: 100 }).notNull(),
  /** How many matches (shares) this pack provides */
  matches: integer('matches').notNull(),
  /** Catalog price in millimes */
  priceMillimes: bigint('price_millimes', { mode: 'bigint' }).notNull(),
  /** Days from purchase until expiry */
  validityDays: integer('validity_days').notNull(),
  sport: varchar('sport', { length: 10 }).notNull().default('PADEL'),
  isActive: boolean('is_active').notNull().default(true),
});

// ── user_pack (purchased pack instances) ─────────────────────
export const userPacks = padelSchema.table(
  'user_pack',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id),
    packId: uuid('pack_id')
      .notNull()
      .references(() => packs.id),
    totalMatches: integer('total_matches').notNull(),
    remaining: integer('remaining').notNull(),
    purchasedAt: timestamp('purchased_at', { withTimezone: true }).notNull().defaultNow(),
    expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
  },
  (t) => ({
    /** FIFO non-expired pack consumption ordering (ARCHITECTURE.md §9) */
    remainingIdx: index('idx_userpack_remaining').on(t.userId, t.expiresAt),
    // WHERE remaining > 0 — raw partial index in 0000_init.sql
  }),
);

// ── pack_consumption_lines (spread across packs per ARCHITECTURE.md §11.2) ──
export const packConsumptionLines = padelSchema.table('pack_consumption_line', {
  id: uuid('id').defaultRandom().primaryKey(),
  bookingId: uuid('booking_id')
    .notNull()
    .references(() => bookings.id),
  userPackId: uuid('user_pack_id')
    .notNull()
    .references(() => userPacks.id),
  matchesConsumed: integer('matches_consumed').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
});

// ── tournaments ───────────────────────────────────────────────
export const tournaments = padelSchema.table('tournament', {
  id: uuid('id').defaultRandom().primaryKey(),
  name: varchar('name', { length: 150 }).notNull(),
  kind: varchar('kind', { length: 20 }).notNull(), // AMERICANO | KNOCKOUT
  startsAt: timestamp('starts_at', { withTimezone: true }).notNull(),
  capacity: integer('capacity').notNull(),
  /** Registration fee in millimes */
  priceMillimes: bigint('price_millimes', { mode: 'bigint' }).notNull(),
  status: varchar('status', { length: 20 }).notNull().default('OPEN'),
  prize: varchar('prize', { length: 255 }),
});

// ── tournament_registrations ───────────────────────────────────
export const tournamentRegistrations = padelSchema.table('tournament_registration', {
  id: uuid('id').defaultRandom().primaryKey(),
  tournamentId: uuid('tournament_id')
    .notNull()
    .references(() => tournaments.id),
  userId: uuid('user_id')
    .notNull()
    .references(() => users.id),
  paid: boolean('paid').notNull().default(false),
  registeredAt: timestamp('registered_at', { withTimezone: true }).notNull().defaultNow(),
});

export type Court = typeof courts.$inferSelect;
export type Slot = typeof slots.$inferSelect;
export type Booking = typeof bookings.$inferSelect;
export type Pack = typeof packs.$inferSelect;
export type UserPack = typeof userPacks.$inferSelect;
export type Tournament = typeof tournaments.$inferSelect;
