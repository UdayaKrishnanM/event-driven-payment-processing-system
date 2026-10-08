package com.payments.common.kafka;

import com.payments.common.domain.IllegalStateTransitionException;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;

/**
 * Builds the Kafka error handler used by every service:
 * <ul>
 *   <li>Temporary errors are retried with exponential backoff (1s, 2s, 4s by default).</li>
 *   <li>After the last retry the record is published to {@code <topic>.DLT} on the same partition.</li>
 *   <li>Permanent errors (bad data, illegal state moves, undeserializable bytes) skip retries and go straight to the DLT,
 *       so they never block the partition.</li>
 * </ul>
 */
@Slf4j
public final class KafkaErrorHandlers {

    private KafkaErrorHandlers() {
    }

    public static DefaultErrorHandler exponentialBackoffWithDlt(KafkaOperations<?, ?> template, int maxRetries,
                                                                long initialIntervalMs, double multiplier,
                                                                long maxIntervalMs) {
        var recoverer = new DeadLetterPublishingRecoverer(template, KafkaErrorHandlers::dltDestination);

        var backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(maxIntervalMs);

        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(
                ValidationException.class,
                IllegalStateTransitionException.class,
                DeserializationException.class);
        handler.setRetryListeners((record, ex, deliveryAttempt) -> logFailedAttempt(record, ex, deliveryAttempt));
        return handler;
    }

    /** Same topic name + ".DLT", same partition (DLT topics have the same partition count). */
    public static TopicPartition dltDestination(ConsumerRecord<?, ?> record, Exception ex) {
        return new TopicPartition(Topics.dltOf(record.topic()), record.partition());
    }

    static void logFailedAttempt(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
        log.warn("Delivery attempt {} failed for topic={} partition={} offset={} key={}: {}",
                deliveryAttempt, record.topic(), record.partition(), record.offset(), record.key(),
                rootMessage(ex));
    }

    static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
