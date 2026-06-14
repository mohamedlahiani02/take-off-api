/**
 * Content schema — partners, faq, hours
 * Ref: ARCHITECTURE.md §3.6 (Mega-menu, gateway, content)
 *
 * CMS-ish: content is DB-backed JSON editable by ops without a redeploy.
 * Served with long-lived ETags (rarely changes).
 */
import {
  pgSchema,
  uuid,
  varchar,
  text,
  boolean,
  integer,
  jsonb,
} from 'drizzle-orm/pg-core';

export const contentSchema = pgSchema('content');

// ── partners ──────────────────────────────────────────────────
export const partners = contentSchema.table('partner', {
  id: uuid('id').defaultRandom().primaryKey(),
  name: varchar('name', { length: 100 }).notNull(),
  logoUrl: varchar('logo_url', { length: 500 }),
  url: varchar('url', { length: 500 }),
  displayOrder: integer('display_order').notNull().default(0),
  isActive: boolean('is_active').notNull().default(true),
});

// ── faq ───────────────────────────────────────────────────────
export const faqItems = contentSchema.table('faq_item', {
  id: uuid('id').defaultRandom().primaryKey(),
  question: text('question').notNull(),
  answer: text('answer').notNull(),
  /** Section the FAQ item belongs to (e.g. 'padel', 'pilates', 'store', 'general') */
  section: varchar('section', { length: 30 }).notNull().default('general'),
  displayOrder: integer('display_order').notNull().default(0),
  isActive: boolean('is_active').notNull().default(true),
});

// ── hours ─────────────────────────────────────────────────────
export const hours = contentSchema.table('hours', {
  id: uuid('id').defaultRandom().primaryKey(),
  /** padel | pilates */
  sport: varchar('sport', { length: 10 }).notNull(),
  /** ISO weekday 1=Mon … 7=Sun */
  dayOfWeek: integer('day_of_week').notNull(),
  openTime: varchar('open_time', { length: 5 }),  // HH:MM
  closeTime: varchar('close_time', { length: 5 }), // HH:MM
  isClosed: boolean('is_closed').notNull().default(false),
  note: varchar('note', { length: 255 }),
});

export type Partner = typeof partners.$inferSelect;
export type FaqItem = typeof faqItems.$inferSelect;
export type Hours = typeof hours.$inferSelect;
