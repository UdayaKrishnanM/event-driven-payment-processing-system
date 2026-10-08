-- Transactional outbox: events are written here in the same transaction as the business change,
-- then published to Kafka by OutboxPublisher.
CREATE TABLE outbox (
    id         UUID         PRIMARY KEY,     -- = eventId
    topic      VARCHAR(100) NOT NULL,
    event_key  VARCHAR(100) NOT NULL,        -- Kafka key = paymentId
    payload    TEXT         NOT NULL,        -- event JSON
    created_at TIMESTAMPTZ  NOT NULL,
    sent_at    TIMESTAMPTZ
);

CREATE INDEX ix_outbox_unsent ON outbox (created_at) WHERE sent_at IS NULL;
