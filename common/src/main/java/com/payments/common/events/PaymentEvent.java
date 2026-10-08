package com.payments.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The single event envelope shared by every topic.
 * <p>
 * Never carries the full card number - only the masked form ("**** 1111").
 * {@code eventId} is unique per event and is what consumers de-duplicate on.
 * {@code paymentId} is used as the Kafka message key so every event of one payment lands on the same partition.
 */
public record PaymentEvent(
        UUID eventId,
        EventType eventType,
        UUID paymentId,
        String merchantId,
        BigDecimal amount,
        String currency,
        String maskedCard,
        String authCode,
        String declineReason,
        Instant occurredAt,
        int schemaVersion) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Creates a brand-new event with a fresh eventId and the current schema version. */
    public static PaymentEvent create(EventType type, UUID paymentId, String merchantId, BigDecimal amount,
                                      String currency, String maskedCard, String authCode, String declineReason,
                                      Instant occurredAt) {
        return new PaymentEvent(UUID.randomUUID(), type, paymentId, merchantId, amount, currency, maskedCard,
                authCode, declineReason, occurredAt, CURRENT_SCHEMA_VERSION);
    }

    /** Derives the next event in the flow for the same payment (new eventId, new type, new timestamp). */
    public PaymentEvent next(EventType nextType, Instant at) {
        return new PaymentEvent(UUID.randomUUID(), nextType, paymentId, merchantId, amount, currency, maskedCard,
                authCode, declineReason, at, CURRENT_SCHEMA_VERSION);
    }

    /** Kafka message key: the payment id, so all events for one payment stay ordered on one partition. */
    public String key() {
        return paymentId.toString();
    }
}
