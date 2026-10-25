ALTER TABLE payment MODIFY refund_status ENUM('NONE','PROCESSING','REFUNDED','FAILED') NOT NULL DEFAULT 'NONE';

CREATE TABLE refund_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  public_id CHAR(36) NOT NULL UNIQUE,
  order_id BIGINT NOT NULL UNIQUE,
  amount DECIMAL(12,2) NOT NULL,
  status ENUM('PROCESSING','SUCCEEDED','FAILED') NOT NULL DEFAULT 'PROCESSING',
  attempt_no INT NOT NULL DEFAULT 1,
  failure_reason VARCHAR(500),
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES customer_order(id),
  CONSTRAINT ck_refund_amount CHECK (amount >= 0),
  INDEX idx_refund_status_time(status, updated_at)
);

CREATE TABLE refund_event (
  event_id CHAR(36) PRIMARY KEY,
  refund_id BIGINT NOT NULL,
  attempt_no INT NOT NULL,
  outcome ENUM('SUCCEEDED','FAILED') NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  reason VARCHAR(500) NOT NULL DEFAULT '',
  disposition ENUM('APPLIED','DUPLICATE_OUTCOME','STALE_ATTEMPT') NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_refund_event_request FOREIGN KEY (refund_id) REFERENCES refund_request(id),
  INDEX idx_refund_event_request(refund_id, created_at)
);

-- Existing historical PROCESSING payments are deliberately not fabricated into provider requests.
-- New cancellation transactions create both the request and payment state atomically.
