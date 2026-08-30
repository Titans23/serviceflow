-- Deterministic local interview/evaluation fixtures. All orders belong to the seeded customer.
-- Odd suffixes are cancellable; even suffixes are shipped and expose shipment data.
UPDATE customer_order SET status = 'PROCESSING', version = 0 WHERE order_no = 'SF202608280001';
UPDATE payment SET refund_status = 'NONE', refund_requested_at = NULL WHERE order_id = 1;
DELETE FROM order_operation WHERE order_id = 1;

INSERT INTO customer_order(order_no, customer_id, status, total_amount, version) VALUES
  ('SF202608280002', 1, 'SHIPPED',    6499.00, 0),
  ('SF202608280003', 1, 'PROCESSING', 9999.00, 0),
  ('SF202608280004', 1, 'SHIPPED',    4699.00, 0),
  ('SF202608280005', 1, 'PROCESSING', 6499.00, 0),
  ('SF202608280006', 1, 'SHIPPED',    9999.00, 0),
  ('SF202608280007', 1, 'PROCESSING', 4699.00, 0),
  ('SF202608280008', 1, 'SHIPPED',    6499.00, 0),
  ('SF202608280009', 1, 'PROCESSING', 9999.00, 0),
  ('SF202608280010', 1, 'SHIPPED',    4699.00, 0),
  ('SF202608280011', 1, 'PROCESSING', 6499.00, 0),
  ('SF202608280012', 1, 'SHIPPED',    9999.00, 0),
  ('SF202608280013', 1, 'PROCESSING', 4699.00, 0),
  ('SF202608280014', 1, 'SHIPPED',    6499.00, 0),
  ('SF202608280015', 1, 'PROCESSING', 9999.00, 0),
  ('SF202608280016', 1, 'SHIPPED',    4699.00, 0),
  ('SF202608280017', 1, 'PROCESSING', 6499.00, 0),
  ('SF202608280018', 1, 'SHIPPED',    9999.00, 0),
  ('SF202608280019', 1, 'PROCESSING', 4699.00, 0),
  ('SF202608280020', 1, 'SHIPPED',    6499.00, 0);

INSERT INTO order_item(order_id, product_id, quantity, unit_price)
SELECT id,
       CASE MOD(CAST(RIGHT(order_no, 3) AS UNSIGNED) - 1, 3) WHEN 0 THEN 1 WHEN 1 THEN 2 ELSE 3 END,
       1,
       total_amount
FROM customer_order
WHERE order_no BETWEEN 'SF202608280002' AND 'SF202608280020';

INSERT INTO payment(order_id, status, refund_status, paid_at)
SELECT id, 'PAID', 'NONE', DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL CAST(RIGHT(order_no, 3) AS UNSIGNED) DAY)
FROM customer_order
WHERE order_no BETWEEN 'SF202608280002' AND 'SF202608280020';

INSERT INTO shipment_event(order_id, description, location, occurred_at)
SELECT id, '包裹已从华东分拨中心发出', '上海', DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 4 HOUR)
FROM customer_order
WHERE order_no BETWEEN 'SF202608280002' AND 'SF202608280020'
  AND MOD(CAST(RIGHT(order_no, 3) AS UNSIGNED), 2) = 0;
