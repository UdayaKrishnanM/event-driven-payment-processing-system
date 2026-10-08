-- ledger schema (owned by ledger-service only)
CREATE TABLE ledger_entries (
    id         UUID          PRIMARY KEY,
    payment_id UUID          NOT NULL,
    account    VARCHAR(100)  NOT NULL,             -- CUSTOMER_CLEARING or MERCHANT:<merchantId>
    direction  VARCHAR(6)    NOT NULL,
    amount     NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency   CHAR(3)       NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_ledger_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    -- stops double posting even if de-duplication were bypassed
    CONSTRAINT uq_ledger_payment_account_direction UNIQUE (payment_id, account, direction)
);

CREATE INDEX ix_ledger_account ON ledger_entries (account);
