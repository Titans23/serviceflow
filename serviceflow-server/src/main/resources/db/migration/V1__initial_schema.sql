CREATE TABLE customer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(190),
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE app_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role ENUM('CUSTOMER','ADMIN') NOT NULL,
  customer_id BIGINT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_user_customer FOREIGN KEY (customer_id) REFERENCES customer(id)
);

CREATE TABLE product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sku VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  brand VARCHAR(100) NOT NULL,
  model VARCHAR(100) NOT NULL,
  category VARCHAR(64) NOT NULL,
  specs JSON NOT NULL,
  list_price DECIMAL(12,2) NOT NULL,
  sale_status ENUM('ON_SALE','OFF_SALE','DISCONTINUED') NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_product_lookup(category, sale_status),
  INDEX idx_product_model(model)
);

CREATE TABLE customer_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL UNIQUE,
  customer_id BIGINT NOT NULL,
  status ENUM('CREATED','PAID','PROCESSING','SHIPPED','DELIVERED','CANCELLED','REFUNDING','REFUNDED') NOT NULL,
  total_amount DECIMAL(12,2) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
  INDEX idx_order_customer_status_time(customer_id, status, created_at DESC)
);

CREATE TABLE order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES customer_order(id),
  CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES product(id)
);

CREATE TABLE payment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL UNIQUE,
  status ENUM('PENDING','PAID','FAILED') NOT NULL,
  refund_status ENUM('NONE','PROCESSING','REFUNDED') NOT NULL DEFAULT 'NONE',
  paid_at TIMESTAMP(6) NULL,
  refund_requested_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES customer_order(id)
);

CREATE TABLE shipment_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  description VARCHAR(255) NOT NULL,
  location VARCHAR(120),
  occurred_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_shipment_order FOREIGN KEY (order_id) REFERENCES customer_order(id),
  INDEX idx_shipment_order_time(order_id, occurred_at DESC)
);

CREATE TABLE order_operation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id CHAR(36) NOT NULL UNIQUE,
  order_id BIGINT NOT NULL,
  operation_type VARCHAR(32) NOT NULL,
  result_code VARCHAR(64) NOT NULL,
  result_message VARCHAR(255) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_operation_order FOREIGN KEY (order_id) REFERENCES customer_order(id)
);

CREATE TABLE ticket (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  public_id CHAR(36) NOT NULL UNIQUE,
  customer_id BIGINT NOT NULL,
  session_public_id CHAR(36),
  source_action_id CHAR(36) NULL UNIQUE,
  subject VARCHAR(200) NOT NULL,
  description TEXT NOT NULL,
  status ENUM('OPEN','ASSIGNED','RESOLVED','CLOSED') NOT NULL DEFAULT 'OPEN',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_ticket_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
  INDEX idx_ticket_customer_status_time(customer_id, status, created_at DESC)
);

CREATE TABLE chat_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  public_id CHAR(36) NOT NULL UNIQUE,
  customer_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_session_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
  INDEX idx_session_customer_time(customer_id, updated_at DESC)
);

CREATE TABLE chat_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  public_id CHAR(36) NOT NULL UNIQUE,
  session_id BIGINT NOT NULL,
  client_request_id CHAR(36),
  role ENUM('USER','ASSISTANT') NOT NULL,
  content TEXT NOT NULL,
  intent VARCHAR(32),
  metadata JSON,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES chat_session(id),
  UNIQUE KEY uk_session_request(session_id, client_request_id),
  INDEX idx_message_session_time(session_id, created_at)
);

CREATE TABLE knowledge_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  public_id CHAR(36) NOT NULL UNIQUE,
  title VARCHAR(200) NOT NULL,
  document_type ENUM('POLICY','PRODUCT_MANUAL','CUSTOMER_SERVICE_SOP') NOT NULL,
  product_id BIGINT NULL,
  active_version_id BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_document_product FOREIGN KEY (product_id) REFERENCES product(id)
);

CREATE TABLE knowledge_document_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  source_name VARCHAR(255) NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  status ENUM('PENDING','PROCESSING','READY','FAILED') NOT NULL,
  error_message VARCHAR(500),
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_version_document FOREIGN KEY (document_id) REFERENCES knowledge_document(id),
  UNIQUE KEY uk_document_version(document_id, version_no)
);

ALTER TABLE knowledge_document ADD CONSTRAINT fk_active_version FOREIGN KEY (active_version_id) REFERENCES knowledge_document_version(id);

INSERT INTO customer(id, name, email) VALUES (1, '演示客户', 'customer@example.com');
INSERT INTO app_user(username, password_hash, role, customer_id) VALUES
  ('admin', '$2b$12$fnIuJZLY7HoU1N13nlJJFut7SvAYZRre8zjIuuaacRlcVh3yGIBDe', 'ADMIN', NULL),
  ('customer', '$2b$12$LMp0kolR.Pc5i0IYnxagluMow7nG2rkQ/TSyyrxyPTOrXJcwKlC1G', 'CUSTOMER', 1);

INSERT INTO product(id, sku, name, brand, model, category, specs, list_price, sale_status) VALUES
  (1, 'SF-PHONE-A100', 'ServicePhone A100', 'ServiceFlow', 'A100', 'phone', JSON_OBJECT('screen_size','6.5 英寸','storage','256 GB','memory','8 GB','battery','5000 mAh','weight','188 g'), 2999.00, 'ON_SALE'),
  (2, 'SF-PHONE-A100P', 'ServicePhone A100 Pro', 'ServiceFlow', 'A100 Pro', 'phone', JSON_OBJECT('screen_size','6.7 英寸','storage','512 GB','memory','12 GB','battery','5200 mAh','weight','201 g'), 3999.00, 'ON_SALE'),
  (3, 'SF-LAPTOP-L1', 'ServiceBook L1', 'ServiceFlow', 'L1', 'laptop', JSON_OBJECT('processor','SF Core 7','memory','16 GB','storage','1 TB','screen_size','14 英寸','weight','1.35 kg'), 5999.00, 'ON_SALE');

INSERT INTO customer_order(id, order_no, customer_id, status, total_amount, version) VALUES
  (1, 'SF202608280001', 1, 'PROCESSING', 2999.00, 0),
  (2, 'SF202608200002', 1, 'SHIPPED', 3999.00, 0);
INSERT INTO order_item(order_id, product_id, quantity, unit_price) VALUES (1,1,1,2999.00),(2,2,1,3999.00);
INSERT INTO payment(order_id, status, refund_status, paid_at) VALUES (1,'PAID','NONE',CURRENT_TIMESTAMP(6)),(2,'PAID','NONE',CURRENT_TIMESTAMP(6));
INSERT INTO shipment_event(order_id, description, location, occurred_at) VALUES
  (2, '包裹已从分拨中心发出', '上海', DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 4 HOUR)),
  (2, '快件已揽收', '苏州', DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY));
