-- Add indexes on transaction_id and order_code columns in donations table
CREATE INDEX idx_donations_transaction_id ON donations(transaction_id);
CREATE INDEX idx_donations_order_code ON donations(order_code);
