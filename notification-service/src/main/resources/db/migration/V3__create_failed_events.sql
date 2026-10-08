-- Messages that ended up on a *.DLT topic after all retries (or a non-retryable error).
CREATE TABLE failed_events (
    id             UUID         PRIMARY KEY,
    topic          VARCHAR(150) NOT NULL,       -- e.g. payment.authorized.DLT
    original_topic VARCHAR(150) NOT NULL,       -- e.g. payment.authorized (replay target)
    consumer_group VARCHAR(150),                -- which consumer gave up on it
    event_key      VARCHAR(150),
    payload        TEXT,
    error          TEXT,
    dlt_partition  INTEGER      NOT NULL,
    dlt_offset     BIGINT       NOT NULL,
    failed_at      TIMESTAMPTZ  NOT NULL,
    replayed_at    TIMESTAMPTZ,
    replay_count   INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_failed_events_position UNIQUE (topic, dlt_partition, dlt_offset)
);

CREATE INDEX ix_failed_events_failed_at ON failed_events (failed_at DESC);
