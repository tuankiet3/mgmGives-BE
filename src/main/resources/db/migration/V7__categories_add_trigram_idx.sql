-- Enable pg_trgm extension if it doesn't already exist
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Create GIN index for lowercase name and description matching
CREATE INDEX idx_categories_name_trgm ON categories USING gin (lower(name) gin_trgm_ops);
CREATE INDEX idx_categories_description_trgm ON categories USING gin (lower(description) gin_trgm_ops);
