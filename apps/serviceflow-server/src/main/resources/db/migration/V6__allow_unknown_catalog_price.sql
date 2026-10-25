-- Public specification pages do not necessarily provide a verified selling price.
-- NULL means unknown; existing price snapshots are preserved.
ALTER TABLE product MODIFY list_price DECIMAL(12,2) NULL;
