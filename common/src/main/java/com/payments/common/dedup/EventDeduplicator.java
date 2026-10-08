package com.payments.common.dedup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Consumer-side idempotency. Each service has its own {@code processed_events} table.
 * <p>
 * Call {@link #firstDelivery(UUID)} inside the same database transaction as the business write.
 * It uses {@code INSERT ... ON CONFLICT DO NOTHING}, so a duplicate does not abort the PostgreSQL transaction:
 * it simply inserts 0 rows and we skip the event. Because the marker and the business write share one transaction,
 * they commit or roll back together.
 */
@Slf4j
@RequiredArgsConstructor
public class EventDeduplicator {

    static final String INSERT_SQL =
            "INSERT INTO processed_events (event_id, processed_at) VALUES (?, now()) ON CONFLICT (event_id) DO NOTHING";

    private final JdbcTemplate jdbcTemplate;

    /** @return true if this is the first time the event is seen (go ahead), false if it was already processed (skip). */
    public boolean firstDelivery(UUID eventId) {
        int inserted = jdbcTemplate.update(INSERT_SQL, eventId);
        if (inserted == 0) {
            log.info("Skipping duplicate event {}", eventId);
            return false;
        }
        return true;
    }
}
