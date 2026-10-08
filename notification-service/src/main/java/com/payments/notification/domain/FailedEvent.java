package com.payments.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A message that landed on a *.DLT topic, kept for review and replay. */
@Entity
@Table(name = "failed_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FailedEvent {

    @Id
    private UUID id;

    /** The DLT topic it was read from, e.g. payment.authorized.DLT */
    @Column(nullable = false, length = 150)
    private String topic;

    @Column(name = "original_topic", nullable = false, length = 150)
    private String originalTopic;

    @Column(name = "consumer_group", length = 150)
    private String consumerGroup;

    @Column(name = "event_key", length = 150)
    private String key;

    @Column(columnDefinition = "text")
    private String payload;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "dlt_partition", nullable = false)
    private int dltPartition;

    @Column(name = "dlt_offset", nullable = false)
    private long dltOffset;

    @Column(name = "failed_at", nullable = false)
    private Instant failedAt;

    @Column(name = "replayed_at")
    private Instant replayedAt;

    @Column(name = "replay_count", nullable = false)
    private int replayCount;

    @SuppressWarnings("java:S107")
    public static FailedEvent of(String topic, String originalTopic, String consumerGroup, String key, String payload,
                                 String error, int dltPartition, long dltOffset, Instant failedAt) {
        FailedEvent f = new FailedEvent();
        f.id = UUID.randomUUID();
        f.topic = topic;
        f.originalTopic = originalTopic;
        f.consumerGroup = consumerGroup;
        f.key = key;
        f.payload = payload;
        f.error = error;
        f.dltPartition = dltPartition;
        f.dltOffset = dltOffset;
        f.failedAt = failedAt;
        return f;
    }

    public void markReplayed(Instant now) {
        this.replayedAt = now;
        this.replayCount++;
    }
}
