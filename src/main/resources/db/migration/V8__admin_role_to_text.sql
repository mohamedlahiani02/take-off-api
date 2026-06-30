-- Convert admin_role column from PostgreSQL custom ENUM to TEXT so Hibernate
-- can read/write it with standard @Enumerated(EnumType.STRING) without casting issues.
-- The column DEFAULT references the admin_role type, so it must be dropped before
-- the type can be dropped, then re-added as a plain text default.
ALTER TABLE admins ALTER COLUMN role DROP DEFAULT;
ALTER TABLE admins ALTER COLUMN role TYPE TEXT USING role::TEXT;
ALTER TABLE admins ALTER COLUMN role SET DEFAULT 'RECEPTION';
DROP TYPE IF EXISTS admin_role;
