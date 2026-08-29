CREATE TABLE ai_answer_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  public_id CHAR(36) NOT NULL UNIQUE,
  session_id BIGINT NOT NULL,
  client_request_id CHAR(36) NOT NULL,
  prompt_version VARCHAR(32) NOT NULL,
  model_name VARCHAR(100) NOT NULL,
  intent VARCHAR(32),
  product_ids JSON NOT NULL,
  citations JSON NOT NULL,
  degraded BOOLEAN NOT NULL DEFAULT FALSE,
  latency_ms BIGINT NOT NULL,
  result_status ENUM('SUCCESS','FAILED') NOT NULL,
  error_code VARCHAR(100),
  question TEXT NOT NULL,
  answer MEDIUMTEXT,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_audit_session FOREIGN KEY (session_id) REFERENCES chat_session(id),
  UNIQUE KEY uk_audit_request(session_id, client_request_id),
  INDEX idx_audit_time(created_at DESC),
  INDEX idx_audit_intent_status(intent, result_status, created_at DESC)
);

ALTER TABLE ticket
  ADD COLUMN assigned_to VARCHAR(64) NULL AFTER status,
  ADD COLUMN resolution_note VARCHAR(500) NULL AFTER assigned_to,
  ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6) AFTER created_at,
  ADD INDEX idx_ticket_status_updated(status, updated_at DESC);
