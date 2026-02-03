-- Seed reference data: Assets and Markets
-- This migration is idempotent - it uses INSERT ... ON CONFLICT DO NOTHING

-- Insert assets (BTC, ETH, USDT)
INSERT INTO asset (symbol, name, scale) VALUES
    ('BTC', 'Bitcoin', 8),
    ('ETH', 'Ethereum', 18),
    ('USDT', 'Tether', 6)
ON CONFLICT (symbol) DO NOTHING;

-- Insert markets (BTC/USDT, ETH/USDT)
-- Using subqueries to find asset IDs by symbol
INSERT INTO market (base_asset_id, quote_asset_id, symbol, active)
SELECT 
    base.id,
    quote.id,
    base.symbol || '/' || quote.symbol,
    true
FROM asset base, asset quote
WHERE base.symbol = 'BTC' AND quote.symbol = 'USDT'
ON CONFLICT (symbol) DO NOTHING;

INSERT INTO market (base_asset_id, quote_asset_id, symbol, active)
SELECT 
    base.id,
    quote.id,
    base.symbol || '/' || quote.symbol,
    true
FROM asset base, asset quote
WHERE base.symbol = 'ETH' AND quote.symbol = 'USDT'
ON CONFLICT (symbol) DO NOTHING;
