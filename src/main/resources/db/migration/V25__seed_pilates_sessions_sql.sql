-- V25 — Seed pilates sessions (pure SQL, no PL/pgSQL variables).
-- V24 used a DO block; this replaces the approach with an inline INSERT…SELECT
-- so there are no PL/pgSQL variable-resolution issues.
-- Idempotent: skips any (class_type_id, starts_at) pair that already exists.
-- Seeds 8 weeks ahead from the current Monday (Africa/Tunis).

INSERT INTO class_sessions
    (id, class_type_id, instructor_id, starts_at,
     duration_min, max_spots, price_dt, status, created_at, updated_at)

SELECT
    gen_random_uuid(),
    ct.id,

    -- Rotate through PILATES coaches by index; NULL when no PILATES coaches exist
    (SELECT c2.id
     FROM   coaches c2
     WHERE  c2.activity = 'PILATES' AND c2.active = TRUE
     ORDER  BY c2.display_order
     LIMIT  1
     OFFSET t.coach_idx % GREATEST(
                (SELECT COUNT(*)::INT FROM coaches WHERE activity = 'PILATES' AND active = TRUE),
                1
            )
    ),

    -- Date: Monday of current Africa/Tunis week + day_off days + slot time → UTC
    (
        (DATE_TRUNC('week', CURRENT_TIMESTAMP AT TIME ZONE 'Africa/Tunis')::DATE
         + (w.wk * 7 + t.day_off))::TIMESTAMP
        + t.slot::TIME
    ) AT TIME ZONE 'Africa/Tunis',

    50,
    t.spots,
    ct.default_price_dt,
    'SCHEDULED',
    NOW(),
    NOW()

FROM
    generate_series(0, 7) AS w(wk),          -- 8 weeks ahead

    (VALUES
        -- Monday (0)
        (0, 'Sunrise Reformer', '07:00:00', 0,  8),
        (0, 'Reformer Flow',    '08:15:00', 1, 10),
        (0, 'Mat Foundations',  '09:30:00', 2, 12),
        (0, 'Sculpt & Tone',    '11:00:00', 0,  8),
        (0, 'Slow Flow',        '12:30:00', 1, 10),
        (0, 'Reformer Flow',    '17:00:00', 2, 12),
        (0, 'Sculpt & Tone',    '18:15:00', 0,  8),
        (0, 'Slow Flow',        '19:30:00', 1, 10),
        -- Tuesday (1) + prenatal
        (1, 'Sunrise Reformer', '07:00:00', 0,  8),
        (1, 'Reformer Flow',    '08:15:00', 1, 10),
        (1, 'Mat Foundations',  '09:30:00', 2, 12),
        (1, 'Prenatal Pilates', '10:00:00', 2,  8),
        (1, 'Sculpt & Tone',    '11:00:00', 0,  8),
        (1, 'Slow Flow',        '12:30:00', 1, 10),
        (1, 'Reformer Flow',    '17:00:00', 2, 12),
        (1, 'Sculpt & Tone',    '18:15:00', 0,  8),
        (1, 'Slow Flow',        '19:30:00', 1, 10),
        -- Wednesday (2)
        (2, 'Sunrise Reformer', '07:00:00', 0,  8),
        (2, 'Reformer Flow',    '08:15:00', 1, 10),
        (2, 'Mat Foundations',  '09:30:00', 2, 12),
        (2, 'Sculpt & Tone',    '11:00:00', 0,  8),
        (2, 'Slow Flow',        '12:30:00', 1, 10),
        (2, 'Reformer Flow',    '17:00:00', 2, 12),
        (2, 'Sculpt & Tone',    '18:15:00', 0,  8),
        (2, 'Slow Flow',        '19:30:00', 1, 10),
        -- Thursday (3) + prenatal
        (3, 'Sunrise Reformer', '07:00:00', 0,  8),
        (3, 'Reformer Flow',    '08:15:00', 1, 10),
        (3, 'Mat Foundations',  '09:30:00', 2, 12),
        (3, 'Prenatal Pilates', '10:00:00', 2,  8),
        (3, 'Sculpt & Tone',    '11:00:00', 0,  8),
        (3, 'Slow Flow',        '12:30:00', 1, 10),
        (3, 'Reformer Flow',    '17:00:00', 2, 12),
        (3, 'Sculpt & Tone',    '18:15:00', 0,  8),
        (3, 'Slow Flow',        '19:30:00', 1, 10),
        -- Friday (4)
        (4, 'Sunrise Reformer', '07:00:00', 0,  8),
        (4, 'Reformer Flow',    '08:15:00', 1, 10),
        (4, 'Mat Foundations',  '09:30:00', 2, 12),
        (4, 'Sculpt & Tone',    '11:00:00', 0,  8),
        (4, 'Slow Flow',        '12:30:00', 1, 10),
        (4, 'Reformer Flow',    '17:00:00', 2, 12),
        (4, 'Sculpt & Tone',    '18:15:00', 0,  8),
        (4, 'Slow Flow',        '19:30:00', 1, 10),
        -- Saturday (5)
        (5, 'Sunrise Reformer', '08:30:00', 0,  8),
        (5, 'Reformer Flow',    '10:00:00', 1, 10),
        (5, 'Mat Foundations',  '11:30:00', 2, 12),
        (5, 'Slow Flow',        '13:00:00', 0,  8),
        -- Sunday (6)
        (6, 'Sunrise Reformer', '08:30:00', 0,  8),
        (6, 'Reformer Flow',    '10:00:00', 1, 10),
        (6, 'Mat Foundations',  '11:30:00', 2, 12),
        (6, 'Slow Flow',        '13:00:00', 0,  8)
    ) AS t(day_off, class_name, slot, coach_idx, spots)

    JOIN class_types ct ON ct.name = t.class_name AND ct.active = TRUE

WHERE
    -- Only future sessions
    (
        (DATE_TRUNC('week', CURRENT_TIMESTAMP AT TIME ZONE 'Africa/Tunis')::DATE
         + (w.wk * 7 + t.day_off))::TIMESTAMP
        + t.slot::TIME
    ) AT TIME ZONE 'Africa/Tunis' > NOW()

    -- Skip if already exists
    AND NOT EXISTS (
        SELECT 1 FROM class_sessions cs
        WHERE  cs.class_type_id = ct.id
          AND  cs.starts_at = (
              (DATE_TRUNC('week', CURRENT_TIMESTAMP AT TIME ZONE 'Africa/Tunis')::DATE
               + (w.wk * 7 + t.day_off))::TIMESTAMP
              + t.slot::TIME
          ) AT TIME ZONE 'Africa/Tunis'
    );
