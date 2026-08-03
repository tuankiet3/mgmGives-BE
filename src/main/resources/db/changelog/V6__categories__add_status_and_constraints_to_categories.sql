-- 1. Create the custom enum type
CREATE TYPE campaign_category_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'HIDDEN');

-- 2. Add the column referencing the enum type, defaulting to 'APPROVED' for existing data
ALTER TABLE categories
    ADD COLUMN status campaign_category_status NOT NULL DEFAULT 'APPROVED';

-- 3. Add the unique constraint to the name column
CREATE UNIQUE INDEX uk_categories_name_ci
    ON categories (LOWER(name));
