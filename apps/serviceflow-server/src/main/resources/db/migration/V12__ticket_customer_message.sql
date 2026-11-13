ALTER TABLE ticket ADD COLUMN customer_message VARCHAR(500) NULL;
-- Existing resolution notes remain internal. Only explicitly published messages are customer-facing.
