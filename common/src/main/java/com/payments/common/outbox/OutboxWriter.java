package com.payments.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.common.events.PaymentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;

/**
 * Transactional outbox, write side. Called inside the business transaction, so the event row is committed
 * if and only if the business change is committed. {@link OutboxPublisher} sends it to Kafka afterwards.
 * This solves the dual-write problem (DB commit and Kafka send cannot be one transaction).
 */
@RequiredArgsConstructor
public class OutboxWriter {

    static final String INSERT_SQL =
            "INSERT INTO outbox (id, topic, event_key, payload, created_at) VALUES (?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public void write(PaymentEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize event " + event.eventId(), e);
        }
        jdbcTemplate.update(INSERT_SQL, event.eventId(), event.eventType().topic(), event.key(), payload,
                Timestamp.from(event.occurredAt()));
    }
}
