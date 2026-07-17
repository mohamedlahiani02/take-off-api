-- V32 — Seed placeholder photos for coaches and product images.
-- Uses Unsplash CDN URLs (no API key needed). Only updates rows where
-- the photo / image_urls is currently null / empty so admin uploads are never overwritten.

-- ── Coach photos ─────────────────────────────────────────────────────────────

UPDATE coaches SET photo_url = 'https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?w=400&h=400&fit=crop&crop=face'
WHERE first_name = 'Ahmed' AND last_name = 'Ben Salah' AND photo_url IS NULL;

UPDATE coaches SET photo_url = 'https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=400&h=400&fit=crop&crop=face'
WHERE first_name = 'Karim' AND last_name = 'Mansouri' AND photo_url IS NULL;

UPDATE coaches SET photo_url = 'https://images.unsplash.com/photo-1568702846914-96b305d2aaeb?w=400&h=400&fit=crop&crop=face'
WHERE first_name = 'Sami' AND last_name = 'Trabelsi' AND photo_url IS NULL;

UPDATE coaches SET photo_url = 'https://images.unsplash.com/photo-1547347298-4074fc3086f0?w=400&h=400&fit=crop&crop=face'
WHERE first_name = 'Youssef' AND last_name = 'Chaabani' AND photo_url IS NULL;

UPDATE coaches SET photo_url = 'https://images.unsplash.com/photo-1605296867304-46d5465a13f1?w=400&h=400&fit=crop&crop=face'
WHERE first_name = 'Mehdi' AND last_name = 'Jebali' AND photo_url IS NULL;

UPDATE coaches SET photo_url = 'https://images.unsplash.com/photo-1533681904393-9ab6eee7bdb0?w=400&h=400&fit=crop&crop=face'
WHERE first_name = 'Fares' AND last_name = 'Ounissi' AND photo_url IS NULL;

UPDATE coaches SET photo_url = 'https://images.unsplash.com/photo-1552072092-7f9b8d63efcb?w=400&h=400&fit=crop&crop=face'
WHERE first_name = 'Nizar' AND last_name = 'Belhaj' AND photo_url IS NULL;

UPDATE coaches SET photo_url = 'https://images.unsplash.com/photo-1583468323973-4c2e5c8e4db0?w=400&h=400&fit=crop&crop=face'
WHERE first_name = 'Amine' AND last_name = 'Khelifi' AND photo_url IS NULL;

UPDATE coaches SET photo_url = 'https://images.unsplash.com/photo-1518310383802-640c2de311b2?w=400&h=400&fit=crop&crop=face'
WHERE first_name = 'Leila' AND last_name = 'Bouaziz' AND photo_url IS NULL;

UPDATE coaches SET photo_url = 'https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=400&h=400&fit=crop&crop=face'
WHERE first_name = 'Sara' AND last_name = 'Hamdi' AND photo_url IS NULL;

UPDATE coaches SET photo_url = 'https://images.unsplash.com/photo-1506629082955-511b1aa562c8?w=400&h=400&fit=crop&crop=face'
WHERE first_name = 'Rim' AND last_name = 'Ayari' AND photo_url IS NULL;

-- ── Product images ────────────────────────────────────────────────────────────
-- Only seeds rows where image_urls is empty array '{}'. Admin uploads are preserved.

UPDATE products SET image_urls = ARRAY['https://images.unsplash.com/photo-1612736231323-7a72f5f3e45a?w=600&h=600&fit=crop']
WHERE name = 'TakeOff Pro Racket' AND image_urls = '{}';

UPDATE products SET image_urls = ARRAY['https://images.unsplash.com/photo-1578662996442-48f60103fc96?w=600&h=600&fit=crop']
WHERE name = 'Overgrip Pack ×3' AND image_urls = '{}';

UPDATE products SET image_urls = ARRAY['https://images.unsplash.com/photo-1583452387500-72bfb5f8c9d4?w=600&h=600&fit=crop']
WHERE name = 'Hand Protector' AND image_urls = '{}';

UPDATE products SET image_urls = ARRAY['https://images.unsplash.com/photo-1551698618-1dfe5d97d256?w=600&h=600&fit=crop']
WHERE name = 'Pro Balls — Tube' AND image_urls = '{}';

UPDATE products SET image_urls = ARRAY['https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&h=600&fit=crop']
WHERE name = 'Court Racket Bag' AND image_urls = '{}';

UPDATE products SET image_urls = ARRAY['https://images.unsplash.com/photo-1588850561407-ed78c282e89b?w=600&h=600&fit=crop']
WHERE name = 'Club Cap' AND image_urls = '{}';

UPDATE products SET image_urls = ARRAY['https://images.unsplash.com/photo-1556909114-f6e7ad7d3136?w=600&h=600&fit=crop']
WHERE name = 'Grip Socks Studio' AND image_urls = '{}';

UPDATE products SET image_urls = ARRAY['https://images.unsplash.com/photo-1596464716127-f2a82984de30?w=600&h=600&fit=crop']
WHERE name = 'Studio Towel' AND image_urls = '{}';

UPDATE products SET image_urls = ARRAY['https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=600&h=600&fit=crop']
WHERE name = 'Performance Tee' AND image_urls = '{}';

UPDATE products SET image_urls = ARRAY['https://images.unsplash.com/photo-1598971861713-54ad16a7e72e?w=600&h=600&fit=crop']
WHERE name = 'Resistance Band Set' AND image_urls = '{}';

UPDATE products SET image_urls = ARRAY['https://images.unsplash.com/photo-1562183241-840b8af0721e?w=600&h=600&fit=crop']
WHERE name = 'Padel Shorts' AND image_urls = '{}';

UPDATE products SET image_urls = ARRAY['https://images.unsplash.com/photo-1506629082955-511b1aa562c8?w=600&h=600&fit=crop']
WHERE name = 'Seamless Leggings' AND image_urls = '{}';

UPDATE products SET image_urls = ARRAY['https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=600&h=600&fit=crop']
WHERE name = 'Take Off Water Bottle' AND image_urls = '{}';

UPDATE products SET image_urls = ARRAY['https://images.unsplash.com/photo-1544816155-12df9643f363?w=600&h=600&fit=crop']
WHERE name = 'Studio Tote Bag' AND image_urls = '{}';
