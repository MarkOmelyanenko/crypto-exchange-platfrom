-- Market Simulator tables
-- Stores ticker updates and simulated trades for portfolio showcase

-- Ensure UUID extension is available (if not already enabled by V1)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Market tick table (periodic price updates)
CREATE TABLE market_tick (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    market_symbol VARCHAR(20) NOT NULL,
    ts TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_price DECIMAL(38, 18) NOT NULL CHECK (last_price > 0),
    bid DECIMAL(38, 18) NOT NULL CHECK (bid > 0),
    ask DECIMAL(38, 18) NOT NULL CHECK (ask > 0),
    volume DECIMAL(38, 18) NOT NULL CHECK (volume >= 0)
);

CREATE INDEX idx_market_tick_symbol_ts ON market_tick(market_symbol, ts DESC);

-- Market trade table (simulated trades)
CREATE TABLE market_trade (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    market_symbol VARCHAR(20) NOT NULL,
    ts TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    price DECIMAL(38, 18) NOT NULL CHECK (price > 0),
    qty DECIMAL(38, 18) NOT NULL CHECK (qty > 0),
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL'))
);

CREATE INDEX idx_market_trade_symbol_ts ON market_trade(market_symbol, ts DESC);
