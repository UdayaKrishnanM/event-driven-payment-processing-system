package com.payments.common.events;

import com.payments.common.kafka.Topics;

/**
 * The kinds of events that flow through the system. Each type is published to exactly one topic.
 */
public enum EventType {
    PAYMENT_INITIATED(Topics.PAYMENT_INITIATED),
    PAYMENT_AUTHORIZED(Topics.PAYMENT_AUTHORIZED),
    PAYMENT_DECLINED(Topics.PAYMENT_DECLINED),
    PAYMENT_SETTLED(Topics.PAYMENT_SETTLED);

    private final String topic;

    EventType(String topic) {
        this.topic = topic;
    }

    public String topic() {
        return topic;
    }
}
