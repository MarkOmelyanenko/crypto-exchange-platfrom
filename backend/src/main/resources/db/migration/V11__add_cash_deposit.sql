-- V11: Add cash deposit feature (USD deposits with 24h rolling limit)

-- Add cash balance column to user_account
ALTER TABLE user_account ADD COLUMN cash_balance_usd NUMERIC(38, 2) NOT NULL DEFAULT 0 CHECK (cash_balance_usd >= 0);

-- Cash deposit history table (used to enforce 24h rolling limit)
CREATE TABLE cash_deposit (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    amount_usd NUMERIC(38, 2) NOT NULL CHECK (amount_usd > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cash_deposit_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
);

CREATE INDEX idx_cash_deposit_user_created ON cash_deposit(user_id, created_at DESC);
