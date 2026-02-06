-- V7: Add price_tick table for storing Binance-fetched price snapshots
-- Used by the dashboard price history chart

CREATE TABLE price_tick (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol      VARCHAR(10) NOT NULL,
    price_usd   NUMERIC(38,18) NOT NULL,
    ts          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_price_tick_symbol_ts ON price_tick (symbol, ts DESC);

-- Optional: auto-cleanup of old ticks (older than 7 days) via a comment
-- In production, consider a scheduled cleanup job or partitioned table.
