/**
 * Pilates schema — class_definitions, class_sessions, reservations
 * Ref: ARCHITECTURE.md §8 (Pilates aggregates) and §3.3
 */
import {
  pgSchema,
  uuid,
  varchar,
  integer,
  timestamp,
  text,
  index,
} from 'drizzle-orm/pg-core';
import { users } from './identity';

export const pilatesSchema = pgSchema('pilates');

// ── class_definitions ────────────────────────────────────────
export const classDefinitions = pilatesSchema.table('class_definition', {
  id: uuid('id').defaultRandom().primaryKey(),
  slug: varchar('slug', { length: 50 }).notNull().unique(),
  name: varchar('name', { length: 100 }).notNull(),
  /** Reformer | Mat | Sculpt | Prenatal | Private */
  level: varchar('level', { length: 30 }).notNull(),
  durationMinutes: integer('duration_minutes').notNull().default(55),
  description: text('description'),
  isActive: integer('is_active').notNull().default(1),
});

// ── class_sessions ────────────────────────────────────────────
export const classSessions = pilatesSchema.table(
  'class_session',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    classDefId: uuid('class_def_id')
      .notNull()
      .references(() => classDefinitions.id),
    /** Coach / instructor user ID */
    instructorId: uuid('instructor_id').references(() => users.id, { onDelete: 'set null' }),
    startsAt: timestamp('starts_at', { withTimezone: true }).notNull(),
    capacity: integer('capacity').notNull().default(8),
    spotsTaken: integer('spots_taken').notNull().default(0),
    status: varchar('status', { length: 20 }).notNull().default('OPEN'), // OPEN | FULL | CANCELLED
  },
  (t) => ({
    startsAtIdx: index('idx_session_starts_at').on(t.startsAt),
    classDefIdx: index('idx_session_class_def').on(t.classDefId),
  }),
);

// ── reservations ──────────────────────────────────────────────
export const reservations = pilatesSchema.table(
  'reservation',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    sessionId: uuid('session_id')
      .notNull()
      .references(() => classSessions.id),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id),
    status: varchar('status', { length: 20 }).notNull().default('HELD'), // HELD | CONFIRMED | WAITLISTED | CANCELLED
    paymentSource: varchar('payment_source', { length: 10 }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    cancelledAt: timestamp('cancelled_at', { withTimezone: true }),
  },
  (t) => ({
    sessionUserIdx: index('idx_reservation_session_user').on(t.sessionId, t.userId),
    userIdx: index('idx_reservation_user').on(t.userId),
  }),
);

export type ClassDefinition = typeof classDefinitions.$inferSelect;
export type ClassSession = typeof classSessions.$inferSelect;
export type Reservation = typeof reservations.$inferSelect;
