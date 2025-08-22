-- Test data for integration tests
INSERT INTO USER_INFO (id, username, password, full_name, cpf, email, enabled, created_at, updated_at) 
VALUES (1, 'testuser', '$2a$10$abcdefghijklmnopqrstuvwxyz', 'Test User', '12345678901', 'test@example.com', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO USER_INFO (id, username, password, full_name, cpf, email, enabled, created_at, updated_at) 
VALUES (2, 'user2', '$2a$10$abcdefghijklmnopqrstuvwxyz', 'Test User 2', '98765432109', 'user2@example.com', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Create the password_reset_tokens table
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES USER_INFO(id)
);

-- Insert test password reset tokens
INSERT INTO password_reset_tokens (id, token, user_id, created_at, expires_at, used_at, is_used) 
VALUES (1, 'test-token-123', 1, CURRENT_TIMESTAMP - INTERVAL '5' MINUTE, CURRENT_TIMESTAMP + INTERVAL '25' MINUTE, NULL, false);

INSERT INTO password_reset_tokens (id, token, user_id, created_at, expires_at, used_at, is_used) 
VALUES (2, 'expired-token-456', 1, CURRENT_TIMESTAMP - INTERVAL '60' MINUTE, CURRENT_TIMESTAMP - INTERVAL '30' MINUTE, NULL, false);

INSERT INTO password_reset_tokens (id, token, user_id, created_at, expires_at, used_at, is_used) 
VALUES (3, 'used-token-789', 1, CURRENT_TIMESTAMP - INTERVAL '10' MINUTE, CURRENT_TIMESTAMP + INTERVAL '20' MINUTE, CURRENT_TIMESTAMP - INTERVAL '5' MINUTE, true);