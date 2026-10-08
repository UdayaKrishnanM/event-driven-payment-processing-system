package com.payments.notification.service;

import com.payments.common.kafka.Topics;
import com.payments.common.util.CardMasker;
import com.payments.common.web.ResourceNotFoundException;
import com.payments.notification.domain.FailedEvent;
import com.payments.notification.domain.FailedEventRepository;
import com.payments.notification.dto.FailedEventResponse;
import com.payments.notification.dto.PagedResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Stores dead letters in failed_events and republishes a fixed message to its original topic on request. */
@Slf4j
@Service
public class FailedEventService {

    static final int MAX_ERROR_LENGTH = 4000;

    private final FailedEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Clock clock;

    public FailedEventService(FailedEventRepository repository, KafkaTemplate<String, Object> kafkaTemplate,
                              Clock clock) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
    }

    @Transactional
    public void store(ConsumerRecord<String, String> record) {
        if (repository.existsByTopicAndDltPartitionAndDltOffset(record.topic(), record.partition(), record.offset())) {
            return;
        }
        String originalTopic = header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        if (originalTopic == null) {
            originalTopic = Topics.originalOf(record.topic());
        }
        String error = truncate(CardMasker.maskPans(describeError(record)));

        FailedEvent saved = repository.save(FailedEvent.of(record.topic(), originalTopic,
                header(record, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP), record.key(),
                CardMasker.maskPans(record.value()), error, record.partition(), record.offset(), clock.instant()));
        log.error("DEAD LETTER stored id={} topic={} key={} group={} error={}", saved.getId(), record.topic(),
                record.key(), saved.getConsumerGroup(), error);
    }

    @Transactional(readOnly = true)
    public PagedResponse<FailedEventResponse> list(int page, int size) {
        return PagedResponse.from(repository.findAllByOrderByFailedAtDesc(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))), FailedEventResponse::from);
    }

    @Transactional(readOnly = true)
    public FailedEventResponse get(UUID id) {
        return FailedEventResponse.from(find(id));
    }

    /** Republishes the stored payload to the original topic (fix the cause first, e.g. turn chaos mode off). */
    @Transactional
    public FailedEventResponse replay(UUID id) {
        FailedEvent failed = find(id);
        try {
            kafkaTemplate.send(failed.getOriginalTopic(), failed.getKey(), failed.getPayload())
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while replaying failed event " + id, e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not replay failed event " + id + ": " + e.getMessage(), e);
        }
        failed.markReplayed(clock.instant());
        log.info("Replayed failed event {} to {} (replay #{})", id, failed.getOriginalTopic(), failed.getReplayCount());
        return FailedEventResponse.from(failed);
    }

    private FailedEvent find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FAILED_EVENT_NOT_FOUND", "Failed event " + id + " not found"));
    }

    static String header(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null || h.value() == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    /** "ListenerExecutionFailedException (cause: SimulatedLedgerFailureException): message" */
    static String describeError(ConsumerRecord<?, ?> record) {
        String exceptionClass = header(record, KafkaHeaders.DLT_EXCEPTION_FQCN);
        String causeClass = header(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN);
        String message = header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        StringBuilder sb = new StringBuilder(exceptionClass == null ? "UnknownException" : exceptionClass);
        if (causeClass != null) {
            sb.append(" (cause: ").append(causeClass).append(')');
        }
        sb.append(": ").append(message == null ? "no message" : message);
        return sb.toString();
    }

    static String truncate(String s) {
        return s.length() <= MAX_ERROR_LENGTH ? s : s.substring(0, MAX_ERROR_LENGTH);
    }
}
