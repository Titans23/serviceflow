CREATE INDEX idx_order_customer_created_id ON customer_order(customer_id, created_at DESC, id DESC);
