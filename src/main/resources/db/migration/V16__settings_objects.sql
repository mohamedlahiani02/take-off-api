-- Epic I — normalize global_settings values to JSON objects so they map cleanly to
-- Map<String,Any> in Hibernate (the codebase's proven JSONB pattern). Simple settings
-- store their scalar under the "value" key.
DELETE FROM global_settings WHERE key IN ('club.phone','club.whatsapp','club.address','club.hours');
INSERT INTO global_settings (key, value) VALUES
    ('club.phone',    '{"value":"+216 27 314 100"}'),
    ('club.whatsapp', '{"value":"https://wa.me/21627314100"}'),
    ('club.address',  '{"value":""}'),
    ('club.hours',    '{}');
