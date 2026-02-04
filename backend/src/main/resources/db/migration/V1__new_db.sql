CREATE TABLE asset
(
    id         UUID         NOT NULL,
    symbol     VARCHAR(10)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    scale      INTEGER      NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_asset PRIMARY KEY (id)
);

CREATE TABLE balance
(
    id         UUID            NOT NULL,
    user_id    UUID            NOT NULL,
    asset_id   UUID            NOT NULL,
    available  DECIMAL(38, 18) NOT NULL,
    locked     DECIMAL(38, 18) NOT NULL,
    version    INTEGER         NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_balance PRIMARY KEY (id)
);

CREATE TABLE market
(
    id             UUID        NOT NULL,
    base_asset_id  UUID        NOT NULL,
    quote_asset_id UUID        NOT NULL,
    symbol         VARCHAR(20) NOT NULL,
    active         BOOLEAN     NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_market PRIMARY KEY (id)
);

CREATE TABLE "order"
(
    id            UUID            NOT NULL,
    user_id       UUID            NOT NULL,
    market_id     UUID            NOT NULL,
    side          VARCHAR(4)      NOT NULL,
    type          VARCHAR(10)     NOT NULL,
    status        VARCHAR(20)     NOT NULL,
    price         DECIMAL(38, 18),
    amount        DECIMAL(38, 18) NOT NULL,
    filled_amount DECIMAL(38, 18) NOT NULL,
    created_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    version       INTEGER         NOT NULL,
    CONSTRAINT pk_order PRIMARY KEY (id)
);

CREATE TABLE trade
(
    id             UUID            NOT NULL,
    market_id      UUID            NOT NULL,
    maker_order_id UUID            NOT NULL,
    taker_order_id UUID            NOT NULL,
    price          DECIMAL(38, 18) NOT NULL,
    amount         DECIMAL(38, 18) NOT NULL,
    executed_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_trade PRIMARY KEY (id)
);

CREATE TABLE user_account
(
    id         UUID         NOT NULL,
    email      VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_user_account PRIMARY KEY (id)
);

CREATE TABLE wallet_hold
(
    id         UUID            NOT NULL,
    user_id    UUID            NOT NULL,
    asset_id   UUID            NOT NULL,
    amount     DECIMAL(38, 18) NOT NULL,
    status     VARCHAR(20)     NOT NULL,
    ref_type   VARCHAR(50)     NOT NULL,
    ref_id     UUID            NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_wallet_hold PRIMARY KEY (id)
);

ALTER TABLE balance
    ADD CONSTRAINT uc_7da7cc55a0bbc6b585f000bea UNIQUE (user_id, asset_id);

ALTER TABLE asset
    ADD CONSTRAINT uc_asset_symbol UNIQUE (symbol);

ALTER TABLE market
    ADD CONSTRAINT uc_market_symbol UNIQUE (symbol);

ALTER TABLE user_account
    ADD CONSTRAINT uc_user_account_email UNIQUE (email);

CREATE INDEX idx_wallet_hold_ref ON wallet_hold (ref_type, ref_id);

CREATE INDEX idx_wallet_hold_status ON wallet_hold (status);

CREATE INDEX idx_wallet_hold_user_asset ON wallet_hold (user_id, asset_id);

ALTER TABLE balance
    ADD CONSTRAINT FK_BALANCE_ON_ASSET FOREIGN KEY (asset_id) REFERENCES asset (id);

ALTER TABLE balance
    ADD CONSTRAINT FK_BALANCE_ON_USER FOREIGN KEY (user_id) REFERENCES user_account (id);

ALTER TABLE market
    ADD CONSTRAINT FK_MARKET_ON_BASE_ASSET FOREIGN KEY (base_asset_id) REFERENCES asset (id);

ALTER TABLE market
    ADD CONSTRAINT FK_MARKET_ON_QUOTE_ASSET FOREIGN KEY (quote_asset_id) REFERENCES asset (id);

ALTER TABLE "order"
    ADD CONSTRAINT FK_ORDER_ON_MARKET FOREIGN KEY (market_id) REFERENCES market (id);

ALTER TABLE "order"
    ADD CONSTRAINT FK_ORDER_ON_USER FOREIGN KEY (user_id) REFERENCES user_account (id);

ALTER TABLE trade
    ADD CONSTRAINT FK_TRADE_ON_MAKER_ORDER FOREIGN KEY (maker_order_id) REFERENCES "order" (id);

ALTER TABLE trade
    ADD CONSTRAINT FK_TRADE_ON_MARKET FOREIGN KEY (market_id) REFERENCES market (id);

ALTER TABLE trade
    ADD CONSTRAINT FK_TRADE_ON_TAKER_ORDER FOREIGN KEY (taker_order_id) REFERENCES "order" (id);

ALTER TABLE wallet_hold
    ADD CONSTRAINT FK_WALLET_HOLD_ON_ASSET FOREIGN KEY (asset_id) REFERENCES asset (id);

ALTER TABLE wallet_hold
    ADD CONSTRAINT FK_WALLET_HOLD_ON_USER FOREIGN KEY (user_id) REFERENCES user_account (id);