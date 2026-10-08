-- notification schema (owned by notification-service only)
CREATE TABLE notifications (
    id          UUID         PRIMARY KEY,
    merchant_id VARCHAR(64)  NOT NULL,
    payment_id  UUID         NOT NULL,
    type        VARCHAR(40)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_notifications_merchant ON notifications (merchant_id, created_at DESC);
CREATE INDEX ix_notifications_payment ON notifications (payment_id);
