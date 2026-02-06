-- Add popular cross-pairs (non-USDT) so users can trade BTC/ETH, SOL/BTC, etc.
-- These match real Binance trading pairs.

-- BTC quote pairs (XXX/BTC → Binance symbol XXXBTC)
INSERT INTO market (id, base_asset_id, quote_asset_id, symbol, active, created_at)
SELECT gen_random_uuid(), base.id, quote.id, base.symbol || '/BTC', true, CURRENT_TIMESTAMP
FROM asset base, asset quote
WHERE quote.symbol = 'BTC'
  AND base.symbol IN ('ETH', 'BNB', 'SOL', 'XRP', 'ADA', 'DOGE', 'DOT', 'LINK', 'LTC', 'AVAX', 'ATOM', 'UNI')
ON CONFLICT (symbol) DO NOTHING;

-- ETH quote pairs (XXX/ETH → Binance symbol XXXETH)
INSERT INTO market (id, base_asset_id, quote_asset_id, symbol, active, created_at)
SELECT gen_random_uuid(), base.id, quote.id, base.symbol || '/ETH', true, CURRENT_TIMESTAMP
FROM asset base, asset quote
WHERE quote.symbol = 'ETH'
  AND base.symbol IN ('BNB', 'SOL', 'LINK', 'ADA', 'DOT', 'AVAX', 'ATOM', 'UNI')
ON CONFLICT (symbol) DO NOTHING;

-- BNB quote pairs (XXX/BNB → Binance symbol XXXBNB)
INSERT INTO market (id, base_asset_id, quote_asset_id, symbol, active, created_at)
SELECT gen_random_uuid(), base.id, quote.id, base.symbol || '/BNB', true, CURRENT_TIMESTAMP
FROM asset base, asset quote
WHERE quote.symbol = 'BNB'
  AND base.symbol IN ('SOL', 'DOT', 'AVAX', 'ADA')
ON CONFLICT (symbol) DO NOTHING;
