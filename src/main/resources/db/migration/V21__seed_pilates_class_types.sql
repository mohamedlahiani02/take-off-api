-- Seed a catalogue of Pilates class types so the schedule has something to
-- generate sessions from. Only seeds when the table is empty, so it never
-- clobbers class types an admin created through the panel.

INSERT INTO class_types (name, level, duration_min, description, default_price_dt, display_order)
SELECT v.name, v.level, v.duration_min, v.description, v.price, v.ord
FROM (VALUES
    ('Reformer Flow',    'All levels',   50, 'Dynamic full-body reformer sequence — springs, straps, control.',            45, 1),
    ('Mat Foundations',  'Beginner',     45, 'Classical mat work building core awareness and clean technique.',            30, 2),
    ('Sculpt & Tone',    'Intermediate', 50, 'Higher-tempo reformer sculpting for strength and definition.',               45, 3),
    ('Sunrise Reformer', 'All levels',   50, 'An energising early-morning reformer flow to open the day.',                 45, 4),
    ('Prenatal Pilates', 'Beginner',     45, 'Gentle, safe movement tailored for every trimester.',                       40, 5),
    ('Slow Flow',        'All levels',   55, 'Mindful, breath-led mat flow to unwind and lengthen.',                      40, 6)
) AS v(name, level, duration_min, description, price, ord)
WHERE NOT EXISTS (SELECT 1 FROM class_types);
