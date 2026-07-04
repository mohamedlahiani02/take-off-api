-- V24 — Seed pilates class sessions for 5 rolling weeks from current week.
-- Uses NOW() so future sessions are always inserted regardless of when this runs.
-- Idempotent: skips any (class_type_id, starts_at) pair that already exists.

DO $$
DECLARE
    v_week_start  DATE;
    v_coaches     UUID[];
    v_coach_count INT;
BEGIN
    -- Monday of the current week in the club's timezone
    v_week_start := DATE_TRUNC('week', NOW() AT TIME ZONE 'Africa/Tunis')::DATE;

    -- Bail out if class types haven't been seeded yet (V21 guard)
    IF NOT EXISTS (SELECT 1 FROM class_types WHERE name = 'Reformer Flow') THEN RETURN; END IF;

    -- Ordered list of PILATES coaches (wraps around via modulo)
    SELECT ARRAY_AGG(id ORDER BY display_order) INTO v_coaches
    FROM   coaches
    WHERE  activity = 'PILATES' AND active = TRUE;

    v_coach_count := COALESCE(ARRAY_LENGTH(v_coaches, 1), 0);

    INSERT INTO class_sessions
        (id, class_type_id, instructor_id, starts_at, duration_min, max_spots, price_dt, status, created_at, updated_at)
    SELECT
        gen_random_uuid(),
        ct.id,
        CASE WHEN v_coach_count > 0 THEN v_coaches[(t.coach_idx % v_coach_count) + 1] ELSE NULL END,
        -- Build a timestamptz: Monday-of-week + day offset + time, interpreted as Africa/Tunis
        ((v_week_start + (w.wk * 7 + t.day_off))::TIMESTAMP + t.slot::TIME) AT TIME ZONE 'Africa/Tunis',
        50,
        t.spots,
        ct.default_price_dt,
        'SCHEDULED',
        NOW(),
        NOW()
    FROM
        generate_series(0, 4) AS w(wk),     -- 5 weeks ahead
        (VALUES
            -- Monday (day_off=0): standard weekday
            (0, 'Sunrise Reformer', '07:00', 0,  8),
            (0, 'Reformer Flow',    '08:15', 1, 10),
            (0, 'Mat Foundations',  '09:30', 2, 12),
            (0, 'Sculpt & Tone',    '11:00', 0,  8),
            (0, 'Slow Flow',        '12:30', 1, 10),
            (0, 'Reformer Flow',    '17:00', 2, 12),
            (0, 'Sculpt & Tone',    '18:15', 0,  8),
            (0, 'Slow Flow',        '19:30', 1, 10),
            -- Tuesday (day_off=1): weekday + prenatal
            (1, 'Sunrise Reformer', '07:00', 0,  8),
            (1, 'Reformer Flow',    '08:15', 1, 10),
            (1, 'Mat Foundations',  '09:30', 2, 12),
            (1, 'Prenatal Pilates', '10:00', 2,  8),
            (1, 'Sculpt & Tone',    '11:00', 0,  8),
            (1, 'Slow Flow',        '12:30', 1, 10),
            (1, 'Reformer Flow',    '17:00', 2, 12),
            (1, 'Sculpt & Tone',    '18:15', 0,  8),
            (1, 'Slow Flow',        '19:30', 1, 10),
            -- Wednesday (day_off=2): standard weekday
            (2, 'Sunrise Reformer', '07:00', 0,  8),
            (2, 'Reformer Flow',    '08:15', 1, 10),
            (2, 'Mat Foundations',  '09:30', 2, 12),
            (2, 'Sculpt & Tone',    '11:00', 0,  8),
            (2, 'Slow Flow',        '12:30', 1, 10),
            (2, 'Reformer Flow',    '17:00', 2, 12),
            (2, 'Sculpt & Tone',    '18:15', 0,  8),
            (2, 'Slow Flow',        '19:30', 1, 10),
            -- Thursday (day_off=3): weekday + prenatal
            (3, 'Sunrise Reformer', '07:00', 0,  8),
            (3, 'Reformer Flow',    '08:15', 1, 10),
            (3, 'Mat Foundations',  '09:30', 2, 12),
            (3, 'Prenatal Pilates', '10:00', 2,  8),
            (3, 'Sculpt & Tone',    '11:00', 0,  8),
            (3, 'Slow Flow',        '12:30', 1, 10),
            (3, 'Reformer Flow',    '17:00', 2, 12),
            (3, 'Sculpt & Tone',    '18:15', 0,  8),
            (3, 'Slow Flow',        '19:30', 1, 10),
            -- Friday (day_off=4): standard weekday
            (4, 'Sunrise Reformer', '07:00', 0,  8),
            (4, 'Reformer Flow',    '08:15', 1, 10),
            (4, 'Mat Foundations',  '09:30', 2, 12),
            (4, 'Sculpt & Tone',    '11:00', 0,  8),
            (4, 'Slow Flow',        '12:30', 1, 10),
            (4, 'Reformer Flow',    '17:00', 2, 12),
            (4, 'Sculpt & Tone',    '18:15', 0,  8),
            (4, 'Slow Flow',        '19:30', 1, 10),
            -- Saturday (day_off=5): lighter weekend
            (5, 'Sunrise Reformer', '08:30', 0,  8),
            (5, 'Reformer Flow',    '10:00', 1, 10),
            (5, 'Mat Foundations',  '11:30', 2, 12),
            (5, 'Slow Flow',        '13:00', 0,  8),
            -- Sunday (day_off=6): lighter weekend
            (6, 'Sunrise Reformer', '08:30', 0,  8),
            (6, 'Reformer Flow',    '10:00', 1, 10),
            (6, 'Mat Foundations',  '11:30', 2, 12),
            (6, 'Slow Flow',        '13:00', 0,  8)
        ) AS t(day_off, class_name, slot, coach_idx, spots)
        JOIN class_types ct ON ct.name = t.class_name AND ct.active = TRUE
    WHERE
        -- Only insert future sessions
        ((v_week_start + (w.wk * 7 + t.day_off))::TIMESTAMP + t.slot::TIME) AT TIME ZONE 'Africa/Tunis' > NOW()
        -- Skip if a session for this type at this exact time already exists
        AND NOT EXISTS (
            SELECT 1 FROM class_sessions cs
            WHERE  cs.class_type_id = ct.id
              AND  cs.starts_at =
                   ((v_week_start + (w.wk * 7 + t.day_off))::TIMESTAMP + t.slot::TIME) AT TIME ZONE 'Africa/Tunis'
        );
END $$;
