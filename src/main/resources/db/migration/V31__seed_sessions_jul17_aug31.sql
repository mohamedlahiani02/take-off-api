-- V31 — Seed class sessions July 17 – August 31 2026.
-- Uses generate_series so we don't hardcode every date.
-- Africa/Tunis = UTC+1 (no DST). Slots stored in UTC.
-- Idempotent via NOT EXISTS check.

-- ── Weekday slots (Mon–Fri, DOW 1–5) ──────────────────────────────────
INSERT INTO class_sessions (id, class_type_id, starts_at, duration_min, max_spots, price_dt, status, created_at, updated_at)
SELECT gen_random_uuid(), ct.id, s.ts, 50, s.spots, ct.default_price_dt, 'SCHEDULED', NOW(), NOW()
FROM (
    SELECT class_name, spots,
           (gs.d + (utc_min || ' minutes')::interval)::TIMESTAMPTZ AS ts
    FROM generate_series('2026-07-17'::date, '2026-08-31'::date, '1 day') AS gs(d)
    CROSS JOIN (VALUES
        ('Sunrise Reformer',  360,  8),
        ('Reformer Flow',     435, 10),
        ('Mat Foundations',   510, 12),
        ('Sculpt & Tone',     600,  8),
        ('Slow Flow',         690, 10),
        ('Reformer Flow',     960, 12),
        ('Sculpt & Tone',    1035,  8),
        ('Slow Flow',        1110, 10)
    ) AS slots(class_name, utc_min, spots)
    WHERE EXTRACT(DOW FROM gs.d) BETWEEN 1 AND 5
) AS s(class_name, spots, ts)
JOIN class_types ct ON ct.name = s.class_name AND ct.active = TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM class_sessions x
    WHERE x.class_type_id = ct.id AND x.starts_at = s.ts
);

-- ── Weekend slots (Sat–Sun, DOW 0 and 6) ──────────────────────────────
INSERT INTO class_sessions (id, class_type_id, starts_at, duration_min, max_spots, price_dt, status, created_at, updated_at)
SELECT gen_random_uuid(), ct.id, s.ts, 50, s.spots, ct.default_price_dt, 'SCHEDULED', NOW(), NOW()
FROM (
    SELECT class_name, spots,
           (gs.d + (utc_min || ' minutes')::interval)::TIMESTAMPTZ AS ts
    FROM generate_series('2026-07-17'::date, '2026-08-31'::date, '1 day') AS gs(d)
    CROSS JOIN (VALUES
        ('Sunrise Reformer', 450,  8),
        ('Reformer Flow',    540, 10),
        ('Mat Foundations',  630, 12),
        ('Slow Flow',        720,  8)
    ) AS slots(class_name, utc_min, spots)
    WHERE EXTRACT(DOW FROM gs.d) IN (0, 6)
) AS s(class_name, spots, ts)
JOIN class_types ct ON ct.name = s.class_name AND ct.active = TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM class_sessions x
    WHERE x.class_type_id = ct.id AND x.starts_at = s.ts
);

-- ── Prenatal Pilates — Tue & Thu only at 09:00 UTC (10:00 TN) ─────────
INSERT INTO class_sessions (id, class_type_id, starts_at, duration_min, max_spots, price_dt, status, created_at, updated_at)
SELECT gen_random_uuid(), ct.id, (gs.d + interval '9 hours')::TIMESTAMPTZ, 50, 8, ct.default_price_dt, 'SCHEDULED', NOW(), NOW()
FROM generate_series('2026-07-17'::date, '2026-08-31'::date, '1 day') AS gs(d)
JOIN class_types ct ON ct.name = 'Prenatal Pilates' AND ct.active = TRUE
WHERE EXTRACT(DOW FROM gs.d) IN (2, 4)
AND NOT EXISTS (
    SELECT 1 FROM class_sessions x
    WHERE x.class_type_id = ct.id AND x.starts_at = (gs.d + interval '9 hours')::TIMESTAMPTZ
);
