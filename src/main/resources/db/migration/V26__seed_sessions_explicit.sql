-- V26 — Explicit pilates session timestamps, no arithmetic.
-- V24/V25 used dynamic date calculations that may have been silently skipped.
-- This seeds a full schedule for July 6–14 2026 with literal UTC timestamps.
-- Africa/Tunis = UTC+1. Idempotent via NOT EXISTS on class_type + starts_at.

-- Helper: resolve class_type_id by name once, then reference in inserts.
-- Uses a single INSERT … SELECT per class type to avoid repeated subqueries.

-- ─────────────────────────────────────────────────────────────
-- MONDAY 7 Jul + FRIDAY 11 Jul + MONDAY 14 Jul  (weekday template)
-- ─────────────────────────────────────────────────────────────
INSERT INTO class_sessions (id, class_type_id, starts_at, duration_min, max_spots, price_dt, status, created_at, updated_at)
SELECT gen_random_uuid(), ct.id, s.ts, 50, s.spots, ct.default_price_dt, 'SCHEDULED', NOW(), NOW()
FROM class_types ct
JOIN (VALUES
    ('Sunrise Reformer', '2026-07-07 06:00:00+00',  8),
    ('Reformer Flow',    '2026-07-07 07:15:00+00', 10),
    ('Mat Foundations',  '2026-07-07 08:30:00+00', 12),
    ('Sculpt & Tone',    '2026-07-07 10:00:00+00',  8),
    ('Slow Flow',        '2026-07-07 11:30:00+00', 10),
    ('Reformer Flow',    '2026-07-07 16:00:00+00', 12),
    ('Sculpt & Tone',    '2026-07-07 17:15:00+00',  8),
    ('Slow Flow',        '2026-07-07 18:30:00+00', 10),
    -- Fri 11
    ('Sunrise Reformer', '2026-07-11 06:00:00+00',  8),
    ('Reformer Flow',    '2026-07-11 07:15:00+00', 10),
    ('Mat Foundations',  '2026-07-11 08:30:00+00', 12),
    ('Sculpt & Tone',    '2026-07-11 10:00:00+00',  8),
    ('Slow Flow',        '2026-07-11 11:30:00+00', 10),
    ('Reformer Flow',    '2026-07-11 16:00:00+00', 12),
    ('Sculpt & Tone',    '2026-07-11 17:15:00+00',  8),
    ('Slow Flow',        '2026-07-11 18:30:00+00', 10),
    -- Mon 14
    ('Sunrise Reformer', '2026-07-14 06:00:00+00',  8),
    ('Reformer Flow',    '2026-07-14 07:15:00+00', 10),
    ('Mat Foundations',  '2026-07-14 08:30:00+00', 12),
    ('Sculpt & Tone',    '2026-07-14 10:00:00+00',  8),
    ('Slow Flow',        '2026-07-14 11:30:00+00', 10),
    ('Reformer Flow',    '2026-07-14 16:00:00+00', 12),
    ('Sculpt & Tone',    '2026-07-14 17:15:00+00',  8),
    ('Slow Flow',        '2026-07-14 18:30:00+00', 10)
) AS s(class_name, ts, spots) ON ct.name = s.class_name AND ct.active = TRUE
WHERE NOT EXISTS (SELECT 1 FROM class_sessions x WHERE x.class_type_id = ct.id AND x.starts_at = s.ts::TIMESTAMPTZ);

-- ─────────────────────────────────────────────────────────────
-- TUESDAY 8 Jul + THURSDAY 10 Jul + TUESDAY 15 Jul  (weekday + prenatal)
-- ─────────────────────────────────────────────────────────────
INSERT INTO class_sessions (id, class_type_id, starts_at, duration_min, max_spots, price_dt, status, created_at, updated_at)
SELECT gen_random_uuid(), ct.id, s.ts, 50, s.spots, ct.default_price_dt, 'SCHEDULED', NOW(), NOW()
FROM class_types ct
JOIN (VALUES
    ('Sunrise Reformer', '2026-07-08 06:00:00+00',  8),
    ('Reformer Flow',    '2026-07-08 07:15:00+00', 10),
    ('Mat Foundations',  '2026-07-08 08:30:00+00', 12),
    ('Prenatal Pilates', '2026-07-08 09:00:00+00',  8),
    ('Sculpt & Tone',    '2026-07-08 10:00:00+00',  8),
    ('Slow Flow',        '2026-07-08 11:30:00+00', 10),
    ('Reformer Flow',    '2026-07-08 16:00:00+00', 12),
    ('Sculpt & Tone',    '2026-07-08 17:15:00+00',  8),
    ('Slow Flow',        '2026-07-08 18:30:00+00', 10),
    -- Thu 10
    ('Sunrise Reformer', '2026-07-10 06:00:00+00',  8),
    ('Reformer Flow',    '2026-07-10 07:15:00+00', 10),
    ('Mat Foundations',  '2026-07-10 08:30:00+00', 12),
    ('Prenatal Pilates', '2026-07-10 09:00:00+00',  8),
    ('Sculpt & Tone',    '2026-07-10 10:00:00+00',  8),
    ('Slow Flow',        '2026-07-10 11:30:00+00', 10),
    ('Reformer Flow',    '2026-07-10 16:00:00+00', 12),
    ('Sculpt & Tone',    '2026-07-10 17:15:00+00',  8),
    ('Slow Flow',        '2026-07-10 18:30:00+00', 10),
    -- Tue 15
    ('Sunrise Reformer', '2026-07-15 06:00:00+00',  8),
    ('Reformer Flow',    '2026-07-15 07:15:00+00', 10),
    ('Mat Foundations',  '2026-07-15 08:30:00+00', 12),
    ('Prenatal Pilates', '2026-07-15 09:00:00+00',  8),
    ('Sculpt & Tone',    '2026-07-15 10:00:00+00',  8),
    ('Slow Flow',        '2026-07-15 11:30:00+00', 10),
    ('Reformer Flow',    '2026-07-15 16:00:00+00', 12),
    ('Sculpt & Tone',    '2026-07-15 17:15:00+00',  8),
    ('Slow Flow',        '2026-07-15 18:30:00+00', 10)
) AS s(class_name, ts, spots) ON ct.name = s.class_name AND ct.active = TRUE
WHERE NOT EXISTS (SELECT 1 FROM class_sessions x WHERE x.class_type_id = ct.id AND x.starts_at = s.ts::TIMESTAMPTZ);

-- ─────────────────────────────────────────────────────────────
-- WEDNESDAY 9 Jul + WEDNESDAY 16 Jul  (weekday)
-- ─────────────────────────────────────────────────────────────
INSERT INTO class_sessions (id, class_type_id, starts_at, duration_min, max_spots, price_dt, status, created_at, updated_at)
SELECT gen_random_uuid(), ct.id, s.ts, 50, s.spots, ct.default_price_dt, 'SCHEDULED', NOW(), NOW()
FROM class_types ct
JOIN (VALUES
    ('Sunrise Reformer', '2026-07-09 06:00:00+00',  8),
    ('Reformer Flow',    '2026-07-09 07:15:00+00', 10),
    ('Mat Foundations',  '2026-07-09 08:30:00+00', 12),
    ('Sculpt & Tone',    '2026-07-09 10:00:00+00',  8),
    ('Slow Flow',        '2026-07-09 11:30:00+00', 10),
    ('Reformer Flow',    '2026-07-09 16:00:00+00', 12),
    ('Sculpt & Tone',    '2026-07-09 17:15:00+00',  8),
    ('Slow Flow',        '2026-07-09 18:30:00+00', 10),
    -- Wed 16
    ('Sunrise Reformer', '2026-07-16 06:00:00+00',  8),
    ('Reformer Flow',    '2026-07-16 07:15:00+00', 10),
    ('Mat Foundations',  '2026-07-16 08:30:00+00', 12),
    ('Sculpt & Tone',    '2026-07-16 10:00:00+00',  8),
    ('Slow Flow',        '2026-07-16 11:30:00+00', 10),
    ('Reformer Flow',    '2026-07-16 16:00:00+00', 12),
    ('Sculpt & Tone',    '2026-07-16 17:15:00+00',  8),
    ('Slow Flow',        '2026-07-16 18:30:00+00', 10)
) AS s(class_name, ts, spots) ON ct.name = s.class_name AND ct.active = TRUE
WHERE NOT EXISTS (SELECT 1 FROM class_sessions x WHERE x.class_type_id = ct.id AND x.starts_at = s.ts::TIMESTAMPTZ);

-- ─────────────────────────────────────────────────────────────
-- SATURDAY 12 Jul + SUNDAY 13 Jul  (weekend — lighter)
-- ─────────────────────────────────────────────────────────────
INSERT INTO class_sessions (id, class_type_id, starts_at, duration_min, max_spots, price_dt, status, created_at, updated_at)
SELECT gen_random_uuid(), ct.id, s.ts, 50, s.spots, ct.default_price_dt, 'SCHEDULED', NOW(), NOW()
FROM class_types ct
JOIN (VALUES
    ('Sunrise Reformer', '2026-07-12 07:30:00+00',  8),
    ('Reformer Flow',    '2026-07-12 09:00:00+00', 10),
    ('Mat Foundations',  '2026-07-12 10:30:00+00', 12),
    ('Slow Flow',        '2026-07-12 12:00:00+00',  8),
    -- Sun 13
    ('Sunrise Reformer', '2026-07-13 07:30:00+00',  8),
    ('Reformer Flow',    '2026-07-13 09:00:00+00', 10),
    ('Mat Foundations',  '2026-07-13 10:30:00+00', 12),
    ('Slow Flow',        '2026-07-13 12:00:00+00',  8)
) AS s(class_name, ts, spots) ON ct.name = s.class_name AND ct.active = TRUE
WHERE NOT EXISTS (SELECT 1 FROM class_sessions x WHERE x.class_type_id = ct.id AND x.starts_at = s.ts::TIMESTAMPTZ);
