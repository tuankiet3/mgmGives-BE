-- Migration V14: Add message and goods details to donations table
ALTER TABLE donations ADD COLUMN message VARCHAR(280);
ALTER TABLE donations ADD COLUMN is_message_hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE donations ADD COLUMN goods_condition VARCHAR(50);
ALTER TABLE donations ADD COLUMN goods_category VARCHAR(50);
ALTER TABLE donations ADD COLUMN delivery_method VARCHAR(50);
ALTER TABLE donations ALTER COLUMN amount DROP NOT NULL;
