-- Add quote_amount column to trade table
-- This stores price * amount to avoid recalculation

-- Add column as nullable first
ALTER TABLE trade
    ADD COLUMN IF NOT EXISTS quote_amount DECIMAL(38, 18);

-- Update existing trades to calculate quote_amount (if any exist)
UPDATE trade SET quote_amount = price * amount WHERE quote_amount IS NULL;

-- Now set NOT NULL constraint
ALTER TABLE trade
    ALTER COLUMN quote_amount SET NOT NULL;

-- Add default value for future inserts (though we'll always set it explicitly)
ALTER TABLE trade
    ALTER COLUMN quote_amount SET DEFAULT 0;

-- Add indexes for matching engine queries (price-time priority)
CREATE INDEX IF NOT EXISTS idx_order_matching_sell 
ON "order"(market_id, price, created_at)
WHERE side = 'SELL' AND status IN ('NEW', 'PARTIALLY_FILLED');

CREATE INDEX IF NOT EXISTS idx_order_matching_buy 
ON "order"(market_id, price DESC, created_at)
WHERE side = 'BUY' AND status IN ('NEW', 'PARTIALLY_FILLED');

CREATE INDEX IF NOT EXISTS idx_order_market_status_side_price_created 
ON "order"(market_id, status, side, price, created_at);