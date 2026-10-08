package com.payments.common.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class KafkaErrorHandlersTest {

    @Test
    void deadLettersGoToSameTopicPlusDltOnSamePartition() {
        var record = new ConsumerRecord<>("payment.authorized", 2, 15L, "key", "value");

        TopicPartition tp = KafkaErrorHandlers.dltDestination(record, new RuntimeException("boom"));

        assertThat(tp.topic()).isEqualTo("payment.authorized.DLT");
        assertThat(tp.partition()).isEqualTo(2);
    }

    @Test
    void buildsHandler() {
        DefaultErrorHandler handler = KafkaErrorHandlers.exponentialBackoffWithDlt(
                mock(KafkaOperations.class), 3, 1000L, 2.0, 10_000L);

        assertThat(handler).isNotNull();
    }

    @Test
    void rootMessageFindsDeepestCause() {
        var ex = new RuntimeException("outer", new IllegalStateException("db down"));

        assertThat(KafkaErrorHandlers.rootMessage(ex)).isEqualTo("IllegalStateException: db down");
        assertThat(KafkaErrorHandlers.rootMessage(new RuntimeException("only"))).isEqualTo("RuntimeException: only");
    }

    @Test
    void logsFailedAttemptsWithoutThrowing() {
        var record = new ConsumerRecord<>("payment.authorized", 0, 1L, "k", "v");
        assertThatCode(() -> KafkaErrorHandlers.logFailedAttempt(record, new RuntimeException("x"), 2))
                .doesNotThrowAnyException();
    }
}
