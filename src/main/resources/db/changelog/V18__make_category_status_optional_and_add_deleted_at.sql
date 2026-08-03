-- 1. Remove NOT NULL constraint from status column to make it optional
ALTER TABLE categories ALTER COLUMN status DROP NOT NULL;

-- 2. Add the deleted_at column
ALTER TABLE categories ADD COLUMN deleted_at TIMESTAMP;

-- 3. Recreate name unique index as a partial index to ignore soft-deleted categories
DROP INDEX uk_categories_name_ci;
CREATE UNIQUE INDEX uk_categories_name_ci ON categories (LOWER(name)) WHERE (deleted_at IS NULL);
