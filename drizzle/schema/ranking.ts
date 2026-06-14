/**
 * Ranking schema — matches, ladder_entries
 * Ref: ARCHITECTURE.md §8 (Match, LadderEntry) and §11.3 (ELO)
 */
import {
  pgSchema,
  uuid,
  varchar,
  integer,
  timestamp,
  index,
} from 'drizzle-orm/pg-core';
import { users } from './identity';

export const rankingSchema = pgSchema('ranking');

// ── matches ───────────────────────────────────────────────────
export const matches = rankingSchema.table(
  'match',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id),
    opponentText: varchar('opponent_text', { length: 255 }).notNull(),
    partnerText: varchar('partner_text', { length: 255 }),
    result: varchar('result', { length: 1 }).notNull(), // W | L
    score: varchar('score', { length: 50 }),
    /** Estimated opponent level 1–7 used in ELO delta multiplier */
    opponentLevelEst: integer('opponent_level_est'),
    /** ELO snapshot at time of match */
    oldLevel: integer('old_level').notNull(),
    newLevel: integer('new_level').notNull(),
    pointsDelta: integer('points_delta').notNull(),
    playedAt: timestamp('played_at', { withTimezone: true }).notNull().defaultNow(),
    source: varchar('source', { length: 25 }).notNull().default('MANUAL'), // MANUAL | AUTO_FROM_BOOKING
    bookingId: uuid('booking_id'),
  },
  (t) => ({
    userIdx: index('idx_match_user').on(t.userId, t.playedAt),
  }),
);

// ── ladder_entries (materialized monthly snapshot) ────────────
export const ladderEntries = rankingSchema.table(
  'ladder_entry',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id),
    /** Period YYYY-MM */
    period: varchar('period', { length: 7 }).notNull(),
    points: integer('points').notNull(),
    wins: integer('wins').notNull().default(0),
    losses: integer('losses').notNull().default(0),
    rank: integer('rank'),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => ({
    /** Leaderboard query (ARCHITECTURE.md §9) */
    periodPointsIdx: index('idx_ladder_period_points').on(t.period, t.points),
    userPeriodIdx: index('idx_ladder_user_period').on(t.userId, t.period),
  }),
);

export type Match = typeof matches.$inferSelect;
export type LadderEntry = typeof ladderEntries.$inferSelect;
