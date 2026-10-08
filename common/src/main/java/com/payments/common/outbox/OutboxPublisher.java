package com.payments.common.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Transactional outbox, publish side. Every {@code payments.outbox.poll-interval-ms} (default 500 ms) it:
 * <ol>
 *   <li>locks a batch of unsent rows with {@code FOR UPDATE SKIP LOCKED} (safe with several instances),</li>
 *   <li>sends them all to Kafka and waits for the broker acks (acks=all),</li>
 *   <li>marks them sent in the same transaction.</li>
 * </ol>
 * If the app crashes after the send but before the commit, rows are sent again on the next run:
 * delivery is at-least-once, and consumer de-duplication on eventId makes processing effectively exactly-once.
 */
@Slf4j
public class OutboxPublisher {

    static final String SELECT_SQL = "SELECT id, topic, event_key, payload FROM outbox WHERE sent_at IS NULL "
            + "ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED";
    static final String MARK_SENT_SQL = "UPDATE outbox SET sent_at = now() WHERE id = ?";
    static final String CLEANUP_SQL = "DELETE FROM outbox WHERE sent_at < now() - interval '1 day'";
    static final int MAX_LOOPS_PER_RUN = 20;

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final long sendTimeoutMs;

    public OutboxPublisher(JdbcTemplate jdbcTemplate, KafkaTemplate<String, Object> kafkaTemplate,
                           TransactionTemplate transactionTemplate, int batchSize, long sendTimeoutMs) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${payments.outbox.poll-interval-ms:500}")
    public void publishPending() {
        try {
            int loops = 0;
            int sent;
            do {
                sent = publishBatch();
                loops++;
            } while (sent == batchSize && loops < MAX_LOOPS_PER_RUN);
        } catch (RuntimeException e) {
            // Rows stay unsent and are retried on the next tick.
            log.error("Outbox publish failed, will retry: {}", e.getMessage());
        }
    }

    /** Publishes one batch in one transaction. Returns how many rows were sent. */
    public int publishBatch() {
        Integer count = transactionTemplate.execute(status -> {
            List<OutboxMessage> batch = jdbcTemplate.query(SELECT_SQL,
                    (rs, i) -> new OutboxMessage(rs.getObject("id", UUID.class), rs.getString("topic"),
                            rs.getString("event_key"), rs.getString("payload")),
                    batchSize);
            if (batch.isEmpty()) {
                return 0;
            }
            List<CompletableFuture<?>> futures = new ArrayList<>(batch.size());
            for (OutboxMessage m : batch) {
                futures.add(kafkaTemplate.send(m.topic(), m.key(), m.payload()));
            }
            try {
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                        .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while publishing outbox batch", e);
            } catch (Exception e) {
                throw new IllegalStateException("Kafka send failed for outbox batch: " + e.getMessage(), e);
            }
            jdbcTemplate.batchUpdate(MARK_SENT_SQL, batch.stream().map(m -> new Object[]{m.id()}).toList());
            log.debug("Published {} outbox message(s)", batch.size());
            return batch.size();
        });
        return count == null ? 0 : count;
    }

    /** Sent rows are kept for a day for troubleshooting, then removed. */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 60_000L)
    public void cleanup() {
        int deleted = jdbcTemplate.update(CLEANUP_SQL);
        if (deleted > 0) {
            log.info("Removed {} old outbox row(s)", deleted);
        }
    }
}
