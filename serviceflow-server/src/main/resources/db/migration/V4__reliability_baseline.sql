CREATE TABLE chat_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  client_request_id CHAR(36) NOT NULL,
  status ENUM('PROCESSING','COMPLETED','FAILED') NOT NULL,
  user_message_id CHAR(36) NULL,
  assistant_message_id CHAR(36) NULL,
  error_code VARCHAR(64) NULL,
  started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_chat_request_session FOREIGN KEY (session_id) REFERENCES chat_session(id),
  UNIQUE KEY uk_chat_request_session_client(session_id, client_request_id),
  INDEX idx_chat_request_status_time(status, updated_at)
);

CREATE TABLE knowledge_ingest_outbox (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_version_id BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  payload_json JSON NOT NULL,
  status ENUM('PENDING','PUBLISHED') NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  last_error VARCHAR(500) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  published_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_outbox_document_version FOREIGN KEY (document_version_id) REFERENCES knowledge_document_version(id),
  UNIQUE KEY uk_outbox_document_version(document_version_id),
  INDEX idx_outbox_due(status, next_attempt_at, id)
);
