-- Convert admin_role column from PostgreSQL custom ENUM to TEXT so Hibernate
-- can read/write it with standard @Enumerated(EnumType.STRING) without casting issues.
ALTER TABLE admins ALTER COLUMN role TYPE TEXT USING role::TEXT;
DROP TYPE IF EXISTS admin_role;
