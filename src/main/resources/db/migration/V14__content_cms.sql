-- Epic I — Content Management ("Lego" mode): per-page section content,
-- global settings, and a media library. No hardcoded data on public pages.

-- ── site_content ───────────────────────────────────────────────
-- One row per (page, section). `content` JSONB holds the section's editable shape:
-- hero {headline, subtitle, ctaLabel, ctaUrl}, stats [{number,label}], faq [{q,a}], etc.
CREATE TABLE site_content (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    page            TEXT NOT NULL,        -- 'padel','pilates','coaches','store','gateway'
    section_key     TEXT NOT NULL,        -- 'hero','stats','marquee','faq','approach',...
    content         JSONB NOT NULL DEFAULT '{}',
    visible         BOOLEAN NOT NULL DEFAULT TRUE,
    display_order   INT NOT NULL DEFAULT 0,
    updated_by_admin_id UUID REFERENCES admins(id) ON DELETE SET NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (page, section_key)
);

CREATE INDEX idx_site_content_page ON site_content (page, display_order);

-- ── global_settings ────────────────────────────────────────────
-- Sitewide key/value: club phone, WhatsApp link, address, opening hours,
-- nav menu items, public pricing. `value` JSONB allows scalars or structures.
CREATE TABLE global_settings (
    key             TEXT PRIMARY KEY,     -- 'club.phone','club.whatsapp','club.hours','nav.menu',...
    value           JSONB NOT NULL DEFAULT '{}',
    updated_by_admin_id UUID REFERENCES admins(id) ON DELETE SET NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed the values currently hardcoded in the public pages.
INSERT INTO global_settings (key, value) VALUES
    ('club.phone',    '"+216 27 314 100"'),
    ('club.whatsapp', '"https://wa.me/21627314100"'),
    ('club.address',  '""'),
    ('club.hours',    '{}');

-- ── media_assets ───────────────────────────────────────────────
-- Library of uploaded images/videos. `slot_id` ties an asset to an image-slot on a page.
CREATE TABLE media_assets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url             TEXT NOT NULL,
    filename        TEXT,
    content_type    TEXT,
    size_bytes      BIGINT,
    slot_id         TEXT,                 -- unique image-slot id on a public page (nullable)
    uploaded_by_admin_id UUID REFERENCES admins(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_media_slot ON media_assets (slot_id);
