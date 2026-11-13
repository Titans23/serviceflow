ALTER TABLE knowledge_document_version
  ADD COLUMN lease_until TIMESTAMP(6) NULL,
  ADD COLUMN attempt_no INT NOT NULL DEFAULT 1,
  ADD COLUMN retry_of_version_id BIGINT NULL UNIQUE,
  ADD CONSTRAINT fk_knowledge_retry FOREIGN KEY(retry_of_version_id) REFERENCES knowledge_document_version(id),
  ADD INDEX idx_knowledge_lease(status,lease_until);
