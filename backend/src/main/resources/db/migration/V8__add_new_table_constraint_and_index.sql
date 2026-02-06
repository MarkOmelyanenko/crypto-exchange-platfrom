CREATE TABLE price_tick
(
    id        UUID            NOT NULL,
    symbol    VARCHAR(10)     NOT NULL,
    price_usd DECIMAL(38, 18) NOT NULL,
    ts        TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_price_tick PRIMARY KEY (id)
);

ALTER TABLE user_account
    ADD CONSTRAINT uc_user_account_login UNIQUE (login);

CREATE INDEX idx_price_tick_symbol_ts ON price_tick (symbol, ts DESC);