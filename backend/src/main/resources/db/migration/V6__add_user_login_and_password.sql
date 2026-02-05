-- Add login and password_hash columns to user_account table
-- First add columns as nullable
ALTER TABLE user_account
    ADD COLUMN login VARCHAR(50),
    ADD COLUMN password_hash VARCHAR(255);

-- Update existing rows with a default login based on email (before part of @)
-- Set a placeholder password hash that won't work for login (existing users need to reset password)
-- This is a migration helper - in production, you might want to handle this differently
UPDATE user_account
SET 
    login = SPLIT_PART(email, '@', 1),
    password_hash = '$2a$10$PLACEHOLDER_HASH_FOR_EXISTING_USERS_REQUIRES_PASSWORD_RESET'
WHERE login IS NULL;

-- Now make columns NOT NULL
ALTER TABLE user_account
    ALTER COLUMN login SET NOT NULL,
    ALTER COLUMN password_hash SET NOT NULL;

-- Create unique constraint on login
CREATE UNIQUE INDEX idx_user_account_login ON user_account(login);