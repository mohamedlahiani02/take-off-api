/**
 * Identity schema — users, sessions, audit_event
 * Ref: ARCHITECTURE.md §8 (User aggregate)
 */
import {
  pgSchema,
  uuid,
  varchar,
  text,
  boolean,
  timestamp,
  jsonb,
  integer,
  bigint,
  index,
} from 'drizzle-orm/pg-core';

export const identitySchema = pgSchema('identity');

// ── users ────────────────────────────────────────────────────
export const users = identitySchema.table(
  'users',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    email: varchar('email', { length: 255 }).notNull().unique(),
    passwordHash: varchar('password_hash', { length: 255 }).notNull(),
    name: varchar('name', { length: 100 }).notNull(),
    phone: varchar('phone', { length: 30 }),
    // tracks[]: 'padel' | 'pilates'
    tracks: text('tracks').array().notNull().default([]),
    locale: varchar('locale', { length: 5 }).notNull().default('fr'),
    /** RBAC role — USER | STAFF | ADMIN (ARCHITECTURE.md §12) */
    role: varchar('role', { length: 20 }).notNull().default('USER'),
    emailVerifiedAt: timestamp('email_verified_at', { withTimezone: true }),
    /** Self-declared padel level 1–7 (seeded by level survey) */
    padelLevel: integer('padel_level').notNull().default(1),
    padelLevelSelfDeclared: boolean('padel_level_self_declared').notNull().default(false),
    /** ELO points — derived into level, stored for fast reads */
    points: integer('points').notNull().default(0),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
    deletedAt: timestamp('deleted_at', { withTimezone: true }),
  },
  (t) => ({
    emailIdx: index('idx_user_email').on(t.email),
  }),
);

// ── sessions (refresh token store — indexed by user for revoke-all) ──
export const sessions = identitySchema.table(
  'sessions',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    /** Argon2/bcrypt hash of the opaque refresh token */
    tokenHash: varchar('token_hash', { length: 255 }).notNull(),
    deviceFingerprint: varchar('device_fingerprint', { length: 255 }),
    expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => ({
    userIdx: index('idx_session_user').on(t.userId),
  }),
);

// ── audit_event (append-only — no updates, no deletes) ───────
export const auditEvent = identitySchema.table(
  'audit_event',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    actorId: uuid('actor_id').references(() => users.id, { onDelete: 'set null' }),
    action: varchar('action', { length: 100 }).notNull(),
    resourceType: varchar('resource_type', { length: 50 }).notNull(),
    resourceId: uuid('resource_id'),
    /** JSON snapshot of relevant fields before/after */
    payload: jsonb('payload'),
    ip: varchar('ip', { length: 45 }),
    userAgent: text('user_agent'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => ({
    actorIdx: index('idx_audit_actor').on(t.actorId),
    resourceIdx: index('idx_audit_resource').on(t.resourceType, t.resourceId),
  }),
);

export type User = typeof users.$inferSelect;
export type NewUser = typeof users.$inferInsert;
export type Session = typeof sessions.$inferSelect;
export type AuditEvent = typeof auditEvent.$inferSelect;
