-- Keep the session row for operational audit records after the owner removes the conversation.
ALTER TABLE chat_session ADD COLUMN deleted_at TIMESTAMP(6) NULL;
