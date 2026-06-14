/**
 * Outbox schema — outbox_event (transactional outbox pattern)
 * Ref: ARCHITECTURE.md §7 (Outbox pattern) and §8 (OutboxEvent aggregate)
 *
 * Append-only table polled by NotificationsWorker.
 * A partial index on published_at IS NULL makes the poll O(unpublished).
 */
import {
  pgSchema,
  uuid,
  varchar,
  integer,
  timestamp,
  jsonb,
  index,
} from 'drizzle-orm/pg-core';

export const outboxSchema = pgSchema('outbox');

export const outboxEvents = outboxSchema.table(
  'outbox_event',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    aggregateType: varchar('aggregate_type', { length: 50 }).notNull(),
    aggregateId: uuid('aggregate_id').notNull(),
    eventType: varchar('event_type', { length: 100 }).notNull(),
    payload: jsonb('payload').notNull(),
    /** Null until the event has been published to the consumer */
    publishedAt: timestamp('published_at', { withTimezone: true }),
    /** Incremented on each failed dispatch attempt */
    attempts: integer('attempts').notNull().default(0),
    /** ISO timestamp of the last attempt */
    lastAttemptAt: timestamp('last_attempt_at', { withTimezone: true }),
    /** Error message from the last failed attempt */
    lastError: varchar('last_error', { length: 500 }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => ({
    /**
     * Critical: fast poll for unpublished events.
     * Partial index WHERE published_at IS NULL — see 0000_init.sql for the raw SQL.
     * ARCHITECTURE.md §9 notes this explicitly.
     */
    unpublishedIdx: index('idx_outbox_unpublished').on(t.createdAt),
    aggregateIdx: index('idx_outbox_aggregate').on(t.aggregateType, t.aggregateId),
  }),
);

export type OutboxEvent = typeof outboxEvents.$inferSelect;
export type NewOutboxEvent = typeof outboxEvents.$inferInsert;
