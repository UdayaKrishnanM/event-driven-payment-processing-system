-- payment schema (owned by payment-service only)
CREATE TABLE payments (
    id               UUID          PRIMARY KEY,
    idempotency_key  VARCHAR(64)   NOT NULL,
    merchant_id      VARCHAR(64)   NOT NULL,
    masked_card      VARCHAR(19)   NOT NULL,              -- "**** 1111"; the full card number is never stored
    card_fingerprint VARCHAR(64)   NOT NULL,              -- HMAC-SHA256 of the card number, for the velocity rule
    expiry_month     INTEGER       NOT NULL,
    expiry_year      INTEGER       NOT NULL,
    amount           NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency         CHAR(3)       NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    auth_code        VARCHAR(6),
    decline_reason   VARCHAR(40),
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,     -- optimistic locking (@Version)
    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_payments_status CHECK (status IN ('RECEIVED', 'AUTHORIZED', 'SETTLED', 'DECLINED'))
);

CREATE INDEX ix_payments_merchant_created ON payments (merchant_id, created_at DESC);
