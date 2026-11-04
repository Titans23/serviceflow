CREATE TABLE refund_delivery (
  refund_id BIGINT PRIMARY KEY,
  attempt_no INT NOT NULL,
  state VARCHAR(24) NOT NULL DEFAULT 'READY',
  lease_token CHAR(36),
  lease_until TIMESTAMP(6),
  next_run_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  next_publish_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  deadline_at TIMESTAMP(6) NOT NULL,
  query_count INT NOT NULL DEFAULT 0,
  submit_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(500),
  ticket_id CHAR(36),
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  FOREIGN KEY(refund_id) REFERENCES refund_request(id),
  INDEX idx_delivery_due(state,next_publish_at,next_run_at)
);
-- Historical refunds are not automatically submitted to a new channel.
