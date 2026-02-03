-- Initial schema for Crypto Exchange Simulator
-- Uses UUIDs for all primary keys
-- All timestamps are UTC

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- UserAccount table
CREATE TABLE user_account (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_account_email ON user_account(email);

-- Asset table
CREATE TABLE asset (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    symbol VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    scale INTEGER NOT NULL CHECK (scale >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_asset_symbol ON asset(symbol);

-- Market table
CREATE TABLE market (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    base_asset_id UUID NOT NULL,
    quote_asset_id UUID NOT NULL,
    symbol VARCHAR(20) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_market_base_asset FOREIGN KEY (base_asset_id) REFERENCES asset(id) ON DELETE RESTRICT,
    CONSTRAINT fk_market_quote_asset FOREIGN KEY (quote_asset_id) REFERENCES asset(id) ON DELETE RESTRICT,
    CONSTRAINT chk_market_different_assets CHECK (base_asset_id != quote_asset_id)
);

CREATE INDEX idx_market_symbol ON market(symbol);
CREATE INDEX idx_market_active ON market(active);
CREATE INDEX idx_market_base_asset ON market(base_asset_id);
CREATE INDEX idx_market_quote_asset ON market(quote_asset_id);

-- Balance table (user asset balances)
CREATE TABLE balance (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    available NUMERIC(38, 18) NOT NULL DEFAULT 0 CHECK (available >= 0),
    locked NUMERIC(38, 18) NOT NULL DEFAULT 0 CHECK (locked >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_balance_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE,
    CONSTRAINT fk_balance_asset FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE RESTRICT,
    CONSTRAINT uk_balance_user_asset UNIQUE (user_id, asset_id)
);

CREATE INDEX idx_balance_user_id ON balance(user_id);
CREATE INDEX idx_balance_asset_id ON balance(asset_id);

-- Order table
CREATE TABLE "order" (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    market_id UUID NOT NULL,
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    type VARCHAR(10) NOT NULL CHECK (type IN ('LIMIT', 'MARKET')),
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'PARTIALLY_FILLED', 'FILLED', 'CANCELED', 'REJECTED')),
    price NUMERIC(38, 18),
    amount NUMERIC(38, 18) NOT NULL CHECK (amount > 0),
    filled_amount NUMERIC(38, 18) NOT NULL DEFAULT 0 CHECK (filled_amount >= 0 AND filled_amount <= amount),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_market FOREIGN KEY (market_id) REFERENCES market(id) ON DELETE RESTRICT,
    CONSTRAINT chk_order_limit_price CHECK (type = 'MARKET' OR (type = 'LIMIT' AND price IS NOT NULL AND price > 0))
);

CREATE INDEX idx_order_user_id_created_at ON "order"(user_id, created_at DESC);
CREATE INDEX idx_order_market_id_status_created_at ON "order"(market_id, status, created_at);
CREATE INDEX idx_order_status ON "order"(status);
CREATE INDEX idx_order_market_id ON "order"(market_id);

-- Trade table (executed trades)
CREATE TABLE trade (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    market_id UUID NOT NULL,
    maker_order_id UUID NOT NULL,
    taker_order_id UUID NOT NULL,
    price NUMERIC(38, 18) NOT NULL CHECK (price > 0),
    amount NUMERIC(38, 18) NOT NULL CHECK (amount > 0),
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trade_market FOREIGN KEY (market_id) REFERENCES market(id) ON DELETE RESTRICT,
    CONSTRAINT fk_trade_maker_order FOREIGN KEY (maker_order_id) REFERENCES "order"(id) ON DELETE RESTRICT,
    CONSTRAINT fk_trade_taker_order FOREIGN KEY (taker_order_id) REFERENCES "order"(id) ON DELETE RESTRICT
);

CREATE INDEX idx_trade_market_id_executed_at ON trade(market_id, executed_at DESC);
CREATE INDEX idx_trade_maker_order_id ON trade(maker_order_id);
CREATE INDEX idx_trade_taker_order_id ON trade(taker_order_id);
CREATE INDEX idx_trade_executed_at ON trade(executed_at);
