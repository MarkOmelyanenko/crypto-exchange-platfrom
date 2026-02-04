-- Seed reference data: Assets and Markets
-- This migration is idempotent - it uses INSERT ... ON CONFLICT DO NOTHING

-- Insert assets (BTC, ETH, USDT)
INSERT INTO asset (id, symbol, name, scale, created_at) VALUES
    (gen_random_uuid(), 'BTC', 'Bitcoin', 8, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'ETH', 'Ethereum', 18, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'USDT', 'Tether', 6, CURRENT_TIMESTAMP)
ON CONFLICT (symbol) DO NOTHING;

-- Insert markets (BTC/USDT, ETH/USDT)
-- Using subqueries to find asset IDs by symbol
INSERT INTO market (id, base_asset_id, quote_asset_id, symbol, active, created_at)
SELECT 
    gen_random_uuid(),
    base.id,
    quote.id,
    base.symbol || '/' || quote.symbol,
    true,
    CURRENT_TIMESTAMP
FROM asset base, asset quote
WHERE base.symbol = 'BTC' AND quote.symbol = 'USDT'
ON CONFLICT (symbol) DO NOTHING;

INSERT INTO market (id, base_asset_id, quote_asset_id, symbol, active, created_at)
SELECT 
    gen_random_uuid(),
    base.id,
    quote.id,
    base.symbol || '/' || quote.symbol,
    true,
    CURRENT_TIMESTAMP
FROM asset base, asset quote
WHERE base.symbol = 'ETH' AND quote.symbol = 'USDT'
ON CONFLICT (symbol) DO NOTHING;
