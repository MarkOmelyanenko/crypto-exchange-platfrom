-- Seed top 100 crypto assets (by market cap) for the Assets page.
-- Uses ON CONFLICT (symbol) DO NOTHING to avoid duplicates with existing BTC, ETH, USDT.

INSERT INTO asset (id, symbol, name, scale, created_at) VALUES
    -- Already exist: BTC (8), ETH (18), USDT (6) — included for completeness, skipped via ON CONFLICT
    (gen_random_uuid(), 'BTC',   'Bitcoin',              8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'ETH',   'Ethereum',             18, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'USDT',  'Tether',               6,  CURRENT_TIMESTAMP),

    -- Top 4-20
    (gen_random_uuid(), 'BNB',   'BNB',                  8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'SOL',   'Solana',               8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'XRP',   'XRP',                  6,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'USDC',  'USD Coin',             6,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'ADA',   'Cardano',              6,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'DOGE',  'Dogecoin',             8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'AVAX',  'Avalanche',            8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'TRX',   'TRON',                 6,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'DOT',   'Polkadot',             8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'LINK',  'Chainlink',            8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'TON',   'Toncoin',              8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'SHIB',  'Shiba Inu',            2,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'SUI',   'Sui',                  8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'LTC',   'Litecoin',             8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'BCH',   'Bitcoin Cash',         8,  CURRENT_TIMESTAMP),

    -- 21-40
    (gen_random_uuid(), 'ATOM',  'Cosmos',               6,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'UNI',   'Uniswap',              8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'XLM',   'Stellar',              7,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'NEAR',  'NEAR Protocol',        8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'ICP',   'Internet Computer',    8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'FIL',   'Filecoin',             8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'APT',   'Aptos',                8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'HBAR',  'Hedera',               8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'ARB',   'Arbitrum',             8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'OP',    'Optimism',             8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'VET',   'VeChain',              8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'MKR',   'Maker',                8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'GRT',   'The Graph',            8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'ALGO',  'Algorand',             6,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'AAVE',  'Aave',                 8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'FTM',   'Fantom',               8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'RENDER','Render',               8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'INJ',   'Injective',            8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'FET',   'Artificial Superintelligence Alliance', 8, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'STX',   'Stacks',               8,  CURRENT_TIMESTAMP),

    -- 41-60
    (gen_random_uuid(), 'SEI',   'Sei',                  8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'TIA',   'Celestia',             8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'PEPE',  'Pepe',                 2,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'WLD',   'Worldcoin',            8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'JUP',   'Jupiter',              8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'BONK',  'Bonk',                 2,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'FLOKI', 'Floki',                2,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'WIF',   'dogwifhat',            8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'SAND',  'The Sandbox',          8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'MANA',  'Decentraland',         8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'AXS',   'Axie Infinity',        8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'EOS',   'EOS',                  4,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'THETA', 'Theta Network',        8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'XTZ',   'Tezos',                6,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'FLOW',  'Flow',                 8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'IMX',   'Immutable',            8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'EGLD',  'MultiversX',           8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'CHZ',   'Chiliz',               8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'RUNE',  'THORChain',            8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'SNX',   'Synthetix',            8,  CURRENT_TIMESTAMP),

    -- 61-80
    (gen_random_uuid(), 'LDO',   'Lido DAO',             8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'CRV',   'Curve DAO',            8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'DYDX',  'dYdX',                 8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'GALA',  'Gala',                 8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'ENJ',   'Enjin Coin',           8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'MASK',  'Mask Network',         8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'PENDLE','Pendle',               8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'CELO',  'Celo',                 8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'ZIL',   'Zilliqa',              8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'KAVA',  'Kava',                 8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'ROSE',  'Oasis Network',        8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'ZEC',   'Zcash',                8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'NEO',   'Neo',                  8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'QTUM',  'Qtum',                 8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'IOTA',  'IOTA',                 8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'ZRX',   '0x',                   8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'BAT',   'Basic Attention Token', 8, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'MINA',  'Mina Protocol',        8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'CAKE',  'PancakeSwap',          8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'GMT',   'STEPN',                8,  CURRENT_TIMESTAMP),

    -- 81-100
    (gen_random_uuid(), 'APE',   'ApeCoin',              8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'WOO',   'WOO',                  8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'STORJ', 'Storj',                8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'ANKR',  'Ankr',                 8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'SKL',   'SKALE',                8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'BLUR',  'Blur',                 8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'SSV',   'ssv.network',          8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'ENS',   'Ethereum Name Service',8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'COMP',  'Compound',             8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'SUSHI', 'SushiSwap',            8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'YFI',   'yearn.finance',        8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'AR',    'Arweave',              8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'CFX',   'Conflux',              8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'STRK',  'Starknet',             8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'MANTA', 'Manta Network',        8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'JTO',   'Jito',                 8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'ORDI',  'ORDI',                 8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'PYTH',  'Pyth Network',         8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'NOT',   'Notcoin',              8,  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'DASH',  'Dash',                 8,  CURRENT_TIMESTAMP)
ON CONFLICT (symbol) DO NOTHING;

-- Also create markets (XXXUSDT) for all new assets so they can be traded
INSERT INTO market (id, base_asset_id, quote_asset_id, symbol, active, created_at)
SELECT
    gen_random_uuid(),
    a.id,
    q.id,
    a.symbol || '/USDT',
    true,
    CURRENT_TIMESTAMP
FROM asset a
CROSS JOIN asset q
WHERE q.symbol = 'USDT'
  AND a.symbol != 'USDT'
  AND a.symbol != 'USDC'
ON CONFLICT (symbol) DO NOTHING;
