-- V10: Add transaction table for simple BUY/SELL user transactions (MVP)
CREATE TABLE transaction (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    asset_symbol VARCHAR(10) NOT NULL,
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity NUMERIC(38, 18) NOT NULL CHECK (quantity > 0),
    price_usd NUMERIC(38, 18) NOT NULL CHECK (price_usd > 0),
    total_usd NUMERIC(38, 18) NOT NULL CHECK (total_usd > 0),
    fee_usd NUMERIC(38, 18) NOT NULL DEFAULT 0 CHECK (fee_usd >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transaction_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
);

CREATE INDEX idx_transaction_user_created ON transaction(user_id, created_at DESC);
CREATE INDEX idx_transaction_user_symbol ON transaction(user_id, asset_symbol);
CREATE INDEX idx_transaction_user_side ON transaction(user_id, side);
