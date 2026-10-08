package com.payments.notification.dto;

import com.payments.notification.domain.FailedEvent;

import java.time.Instant;
import java.util.UUID;

public record FailedEventResponse(UUID id, String topic, String originalTopic, String consumerGroup, String key,
                                  String payload, String error, int dltPartition, long dltOffset, Instant failedAt,
                                  Instant replayedAt, int replayCount) {

    public static FailedEventResponse from(FailedEvent f) {
        return new FailedEventResponse(f.getId(), f.getTopic(), f.getOriginalTopic(), f.getConsumerGroup(), f.getKey(),
                f.getPayload(), f.getError(), f.getDltPartition(), f.getDltOffset(), f.getFailedAt(),
                f.getReplayedAt(), f.getReplayCount());
    }
}
