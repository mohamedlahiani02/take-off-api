/**
 * Coaching schema — coaches, coaching_inquiries
 * Ref: ARCHITECTURE.md §8 (Coach, CoachingInquiry) and §3.2 (Coaching inquiry)
 */
import {
  pgSchema,
  uuid,
  varchar,
  text,
  timestamp,
  boolean,
  jsonb,
  index,
} from 'drizzle-orm/pg-core';
import { users } from './identity';

export const coachingSchema = pgSchema('coaching');

// ── coaches ────────────────────────────────────────────────────
export const coaches = coachingSchema.table('coach', {
  id: uuid('id').defaultRandom().primaryKey(),
  slug: varchar('slug', { length: 50 }).notNull().unique(),
  name: varchar('name', { length: 100 }).notNull(),
  role: varchar('role', { length: 50 }).notNull(),
  bio: text('bio'),
  specialties: text('specialties').array().notNull().default([]),
  achievements: text('achievements').array().notNull().default([]),
  photoUrl: varchar('photo_url', { length: 500 }),
  sport: varchar('sport', { length: 10 }).notNull(), // PADEL | PILATES
  isActive: boolean('is_active').notNull().default(true),
  /** FK to identity.users if the coach has a platform account */
  userId: uuid('user_id').references(() => users.id, { onDelete: 'set null' }),
});

// ── coaching_inquiries ────────────────────────────────────────
export const coachingInquiries = coachingSchema.table(
  'coaching_inquiry',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    /** Null if the inquiry was submitted anonymously */
    userId: uuid('user_id').references(() => users.id, { onDelete: 'set null' }),
    name: varchar('name', { length: 100 }).notNull(),
    phone: varchar('phone', { length: 30 }).notNull(),
    email: varchar('email', { length: 255 }).notNull(),
    courseTypes: text('course_types').array().notNull().default([]),
    groupNote: text('group_note'),
    /**
     * 7×4 availability matrix: 7 days × 4 slots (morning/noon/afternoon/evening)
     * Stored as JSONB for flexibility.
     */
    availabilityMatrix: jsonb('availability_matrix').$type<boolean[][]>(),
    niveau: varchar('niveau', { length: 20 }),
    otherSports: varchar('other_sports', { length: 255 }),
    motivation: text('motivation'),
    language: varchar('language', { length: 5 }).notNull().default('fr'),
    notes: text('notes'),
    status: varchar('status', { length: 15 }).notNull().default('NEW'), // NEW | CONTACTED | CLOSED | WON
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    contactedAt: timestamp('contacted_at', { withTimezone: true }),
    closedAt: timestamp('closed_at', { withTimezone: true }),
  },
  (t) => ({
    statusIdx: index('idx_inquiry_status').on(t.status, t.createdAt),
    emailIdx: index('idx_inquiry_email').on(t.email),
  }),
);

export type Coach = typeof coaches.$inferSelect;
export type CoachingInquiry = typeof coachingInquiries.$inferSelect;
