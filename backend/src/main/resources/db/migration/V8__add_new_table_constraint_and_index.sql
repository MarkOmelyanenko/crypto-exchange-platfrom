-- V8: Add unique constraint on user_account.login
-- Note: price_tick table and index already created in V7

ALTER TABLE user_account
    ADD CONSTRAINT uc_user_account_login UNIQUE (login);