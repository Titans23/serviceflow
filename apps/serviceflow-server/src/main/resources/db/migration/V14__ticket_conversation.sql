CREATE TABLE ticket_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  public_id CHAR(36) NOT NULL UNIQUE,
  ticket_public_id CHAR(36) NOT NULL,
  author_role VARCHAR(16) NOT NULL,
  content VARCHAR(500) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY(ticket_public_id) REFERENCES ticket(public_id),
  CHECK (author_role IN ('CUSTOMER','SUPPORT')),
  INDEX idx_ticket_message(ticket_public_id,id)
);
INSERT INTO ticket_message(public_id,ticket_public_id,author_role,content,created_at)
SELECT UUID(),public_id,'SUPPORT',customer_message,updated_at FROM ticket
WHERE customer_message IS NOT NULL AND customer_message<>'';
