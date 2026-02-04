-- Quick fix: Run this SQL directly in your database to add the missing column
-- This will allow the application to start, then Flyway will handle future migrations

-- Add quote_amount column
ALTER TABLE trade
    ADD COLUMN IF NOT EXISTS quote_amount DECIMAL(38, 18);

-- Update existing trades (if any)
UPDATE trade SET quote_amount = price * amount WHERE quote_amount IS NULL;

-- Set NOT NULL
ALTER TABLE trade
    ALTER COLUMN quote_amount SET NOT NULL;

-- Set default
ALTER TABLE trade
    ALTER COLUMN quote_amount SET DEFAULT 0;

-- Add indexes
CREATE INDEX IF NOT EXISTS idx_order_matching_sell 
ON "order"(market_id, price, created_at)
WHERE side = 'SELL' AND status IN ('NEW', 'PARTIALLY_FILLED');

CREATE INDEX IF NOT EXISTS idx_order_matching_buy 
ON "order"(market_id, price DESC, created_at)
WHERE side = 'BUY' AND status IN ('NEW', 'PARTIALLY_FILLED');

CREATE INDEX IF NOT EXISTS idx_order_market_status_side_price_created 
ON "order"(market_id, status, side, price, created_at);
