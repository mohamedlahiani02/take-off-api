-- V22 — Comprehensive dummy data seed
-- Idempotent: every section is guarded so a second run is a no-op.

-- ══════════════════════════════════════════════════════════════════════════════
-- SECTION 1 — Tournaments (3 entries, seeded only when the table is empty)
-- ══════════════════════════════════════════════════════════════════════════════
INSERT INTO tournaments (
    title, description, banner_url,
    format, category, starts_at, ends_at,
    entry_fee_dt, prize,
    status, max_participants,
    registration_deadline, registration_mode, payment_rule
)
SELECT * FROM (VALUES
    (
        'Summer Americano',
        'Join us for our signature summer Americano tournament — great competition, great vibes under the Sfax sun.',
        'https://images.unsplash.com/photo-1554068865-24cecd4e34b8?w=800&q=80',
        'AMERICANO', 'MIXED',
        NOW() + interval '14 days', NOW() + interval '15 days',
        80.000::NUMERIC(10,3), 'Trophy + 500 DT shop credit',
        'REGISTRATION_OPEN', 32,
        NOW() + interval '10 days', 'OPEN', 'AT_CLUB'
    ),
    (
        'Take Off Cup',
        'Our flagship knockout cup. 32 competitors, single elimination, one champion.',
        'https://images.unsplash.com/photo-1612872087720-bb876e2e67d1?w=800&q=80',
        'KNOCKOUT', 'MEN',
        NOW() + interval '35 days', NOW() + interval '37 days',
        100.000::NUMERIC(10,3), 'Cup + Pro racket prize pool',
        'REGISTRATION_OPEN', 32,
        NOW() + interval '28 days', 'OPEN', 'BOTH'
    ),
    (
        'Night Mixed',
        'An evening Americano under the lights — mixed doubles, relaxed format, competitive spirit.',
        'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800&q=80',
        'AMERICANO', 'MIXED',
        NOW() + interval '60 days', NOW() + interval '61 days',
        60.000::NUMERIC(10,3), '300 DT cash + bottles',
        'REGISTRATION_OPEN', 24,
        NOW() + interval '53 days', 'OPEN', 'AT_CLUB'
    )
) AS v(title, description, banner_url, format, category, starts_at, ends_at,
       entry_fee_dt, prize, status, max_participants,
       registration_deadline, registration_mode, payment_rule)
WHERE NOT EXISTS (SELECT 1 FROM tournaments LIMIT 1);

-- ══════════════════════════════════════════════════════════════════════════════
-- SECTION 2 — Pack types (6 entries)
-- Each pack is guarded individually by name so this is safe alongside the 5
-- packs already seeded in V13.
-- ══════════════════════════════════════════════════════════════════════════════

-- PADEL packs
INSERT INTO pack_types (name, activity, price_dt, credit_count, unlimited, validity_months, display_order)
SELECT 'Drop-in', 'PADEL', 20.000, 1, FALSE, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM pack_types WHERE name = 'Drop-in');

INSERT INTO pack_types (name, activity, price_dt, credit_count, unlimited, validity_months, display_order)
SELECT 'Pack 10 Matchs', 'PADEL', 190.000, 10, FALSE, 3, 2
WHERE NOT EXISTS (SELECT 1 FROM pack_types WHERE name = 'Pack 10 Matchs');

INSERT INTO pack_types (name, activity, price_dt, credit_count, unlimited, validity_months, display_order)
SELECT 'Pack 25 Matchs', 'PADEL', 450.000, 25, FALSE, 6, 3
WHERE NOT EXISTS (SELECT 1 FROM pack_types WHERE name = 'Pack 25 Matchs');

-- PILATES packs
INSERT INTO pack_types (name, activity, price_dt, credit_count, unlimited, validity_months, display_order)
SELECT 'Séance Découverte', 'PILATES', 35.000, 1, FALSE, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM pack_types WHERE name = 'Séance Découverte');

INSERT INTO pack_types (name, activity, price_dt, credit_count, unlimited, validity_months, display_order)
SELECT 'Pack 10 Séances', 'PILATES', 300.000, 10, FALSE, 3, 2
WHERE NOT EXISTS (SELECT 1 FROM pack_types WHERE name = 'Pack 10 Séances');

INSERT INTO pack_types (name, activity, price_dt, credit_count, unlimited, validity_months, display_order)
SELECT 'Flow Mensuel', 'PILATES', 420.000, NULL, TRUE, 1, 3
WHERE NOT EXISTS (SELECT 1 FROM pack_types WHERE name = 'Flow Mensuel');

-- ══════════════════════════════════════════════════════════════════════════════
-- SECTION 3 — Class type photo URLs (update V21-seeded rows where photo is absent)
-- ══════════════════════════════════════════════════════════════════════════════
UPDATE class_types SET photo_url = CASE name
    WHEN 'Reformer Flow'    THEN 'https://images.unsplash.com/photo-1518611012118-696072aa579a?w=800&q=80'
    WHEN 'Mat Foundations'  THEN 'https://images.unsplash.com/photo-1601925260368-ae2f83cf8b7f?w=800&q=80'
    WHEN 'Sculpt & Tone'    THEN 'https://images.unsplash.com/photo-1607962837359-5e7e89f86776?w=800&q=80'
    WHEN 'Sunrise Reformer' THEN 'https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=800&q=80'
    WHEN 'Prenatal Pilates' THEN 'https://images.unsplash.com/photo-1576678927484-cc907957088c?w=800&q=80'
    WHEN 'Slow Flow'        THEN 'https://images.unsplash.com/photo-1552196563-55cd4e45efb3?w=800&q=80'
END
WHERE name IN ('Reformer Flow','Mat Foundations','Sculpt & Tone','Sunrise Reformer','Prenatal Pilates','Slow Flow')
  AND (photo_url IS NULL OR photo_url = '');

-- ══════════════════════════════════════════════════════════════════════════════
-- SECTION 4 — CMS hero / gateway sections (table: site_content)
-- ON CONFLICT DO NOTHING keeps this idempotent.
-- ══════════════════════════════════════════════════════════════════════════════
INSERT INTO site_content (page, section_key, content, visible, display_order, updated_at)
VALUES
    ('padel',   'hero',
     '{"kicker":"TAKE OFF CLUB — PADEL · SFAX","headline":"Own the\nnight court.","subtitle":"Two pro courts under the lights. Book in seconds, play like a pro."}'::jsonb,
     TRUE, 0, now()),

    ('pilates', 'hero',
     '{"kicker":"PILATES · SFAX","headline":"Move with intention.","subtitle":"Reformer and mat pilates for all levels. Sfax''s first dedicated reformer studio."}'::jsonb,
     TRUE, 0, now()),

    ('coaches', 'hero',
     '{"kicker":"NOTRE ÉQUIPE","headline":"Des coachs\npour chaque niveau.","subtitle":"Certifiés, passionnés, disponibles 7j/7."}'::jsonb,
     TRUE, 0, now()),

    ('store',   'hero',
     '{"kicker":"THE TAKE OFF STORE","headline":"Gear up.","subtitle":"Padel, Pilates & lifestyle. Livraison depuis Sfax."}'::jsonb,
     TRUE, 0, now()),

    ('gateway', 'padel',
     '{"kicker":"01 — THE COURT","subtitle":"Deux courts panoramiques. Réservez en quelques secondes.","cta":"Entrer dans les courts"}'::jsonb,
     TRUE, 1, now()),

    ('gateway', 'pilates',
     '{"kicker":"02 — THE STUDIO","subtitle":"Reformer et tapis. Le premier studio dédié de Sfax.","cta":"Entrer dans le studio"}'::jsonb,
     TRUE, 2, now())

ON CONFLICT (page, section_key) DO NOTHING;
