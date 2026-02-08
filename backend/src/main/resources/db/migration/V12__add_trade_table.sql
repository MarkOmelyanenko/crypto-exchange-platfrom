-- V12: Replace old trade table (V1 matching-engine style) with new spot-market trade table
-- The old trade table (maker_order_id/taker_order_id) is no longer used by the application
DROP TABLE IF EXISTS trade CASCADE;

CREATE TABLE trade (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID          NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    pair_id    UUID          NOT NULL REFERENCES market(id),
    side       VARCHAR(4)    NOT NULL CHECK (side IN ('BUY', 'SELL')),
    price      NUMERIC(38, 18) NOT NULL,
    base_qty   NUMERIC(38, 18) NOT NULL,
    quote_qty  NUMERIC(38, 18) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trade_user_created ON trade(user_id, created_at DESC);
CREATE INDEX idx_trade_pair_created ON trade(pair_id, created_at DESC);
