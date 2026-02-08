-- V14: Create wallet_hold table for tracking reserved funds (e.g., during order placement)
CREATE TABLE wallet_hold (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID            NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    asset_id   UUID            NOT NULL REFERENCES asset(id),
    amount     NUMERIC(38, 18) NOT NULL,
    status     VARCHAR(20)     NOT NULL CHECK (status IN ('ACTIVE', 'RELEASED', 'CAPTURED')),
    ref_type   VARCHAR(50)     NOT NULL,
    ref_id     UUID            NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wallet_hold_user_asset ON wallet_hold(user_id, asset_id);
CREATE INDEX idx_wallet_hold_ref ON wallet_hold(ref_type, ref_id);
CREATE INDEX idx_wallet_hold_status ON wallet_hold(status);
