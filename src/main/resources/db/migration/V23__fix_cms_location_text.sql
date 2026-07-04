-- V23 — Ensure CMS hero sections carry correct location text (Sfax, not Tunis).
-- V22 used ON CONFLICT DO NOTHING, which preserved any pre-existing record.
-- This migration forces the correct content for every hero section we own.

INSERT INTO site_content (page, section_key, content, visible, display_order, updated_at)
VALUES
    ('padel', 'hero',
     '{"kicker":"TAKE OFF CLUB — PADEL · SFAX","headline":"Own the\nnight court.","subtitle":"Two pro courts under the lights. Book in seconds, play like a pro."}'::jsonb,
     TRUE, 0, now()),

    ('pilates', 'hero',
     '{"kicker":"PILATES · SFAX","headline":"Move with intention.","subtitle":"Reformer and mat pilates for all levels. Sfax''s first dedicated reformer studio."}'::jsonb,
     TRUE, 0, now()),

    ('coaches', 'hero',
     '{"kicker":"NOTRE ÉQUIPE","headline":"Des coachs\npour chaque niveau.","subtitle":"Certifiés, passionnés, disponibles 7j/7."}'::jsonb,
     TRUE, 0, now()),

    ('store', 'hero',
     '{"kicker":"THE TAKE OFF STORE","headline":"Gear up.","subtitle":"Padel, Pilates & lifestyle. Livraison depuis Sfax."}'::jsonb,
     TRUE, 0, now()),

    ('gateway', 'padel',
     '{"kicker":"01 — THE COURT","subtitle":"Deux courts panoramiques. Réservez en quelques secondes.","cta":"Entrer dans les courts"}'::jsonb,
     TRUE, 1, now()),

    ('gateway', 'pilates',
     '{"kicker":"02 — THE STUDIO","subtitle":"Reformer et tapis. Le premier studio dédié de Sfax.","cta":"Entrer dans le studio"}'::jsonb,
     TRUE, 2, now())

ON CONFLICT (page, section_key) DO UPDATE
    SET content     = EXCLUDED.content,
        updated_at  = now();
