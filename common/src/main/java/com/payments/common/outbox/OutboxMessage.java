package com.payments.common.outbox;

import java.util.UUID;

/** One unsent row of the outbox table. */
public record OutboxMessage(UUID id, String topic, String key, String payload) {
}
