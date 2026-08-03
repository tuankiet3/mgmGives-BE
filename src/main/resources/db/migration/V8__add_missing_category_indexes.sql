-- Composite index to speed up getApprovedCategories (status = APPROVED order by name asc)
CREATE INDEX idx_categories_status_name ON categories (status, name);

-- Index on join table campaign_categories(category_id) to optimize category to campaign associations lookups
CREATE INDEX idx_campaign_categories_category_id ON campaign_categories (category_id);
