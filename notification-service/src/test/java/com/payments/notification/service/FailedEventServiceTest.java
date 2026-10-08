package com.payments.notification.service;

import com.payments.common.web.ResourceNotFoundException;
import com.payments.notification.domain.FailedEvent;
import com.payments.notification.domain.FailedEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class FailedEventServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:15:30Z");

    private FailedEventRepository repository;
    private KafkaTemplate<String, Object> kafka;
    private FailedEventService service;

    @BeforeEach
    void setUp() {
        repository = mock(FailedEventRepository.class);
        kafka = mock(KafkaTemplate.class);
        service = new FailedEventService(repository, kafka, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any(FailedEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ConsumerRecord<String, String> dltRecord() {
        var record = new ConsumerRecord<>("payment.authorized.DLT", 1, 42L, "pay-1", "{\"card\":\"4111111111111111\"}");
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_TOPIC, "payment.authorized".getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, "ledger-service".getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_FQCN, "o.s.k.ListenerExecutionFailedException".getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN, "SimulatedLedgerFailureException".getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "db down".getBytes(StandardCharsets.UTF_8));
        return record;
    }

    @Test
    void storesDeadLetterWithMaskedPayloadAndErrorDetails() {
        service.store(dltRecord());

        ArgumentCaptor<FailedEvent> saved = ArgumentCaptor.forClass(FailedEvent.class);
        verify(repository).save(saved.capture());
        FailedEvent f = saved.getValue();
        assertThat(f.getTopic()).isEqualTo("payment.authorized.DLT");
        assertThat(f.getOriginalTopic()).isEqualTo("payment.authorized");
        assertThat(f.getConsumerGroup()).isEqualTo("ledger-service");
        assertThat(f.getKey()).isEqualTo("pay-1");
        assertThat(f.getPayload()).doesNotContain("4111111111111111").contains("**** 1111");
        assertThat(f.getError()).contains("SimulatedLedgerFailureException", "db down");
        assertThat(f.getDltPartition()).isEqualTo(1);
        assertThat(f.getDltOffset()).isEqualTo(42L);
        assertThat(f.getFailedAt()).isEqualTo(NOW);
    }

    @Test
    void alreadyStoredPositionIsIgnored() {
        when(repository.existsByTopicAndDltPartitionAndDltOffset("payment.authorized.DLT", 1, 42L)).thenReturn(true);

        service.store(dltRecord());

        verify(repository, never()).save(any());
    }

    @Test
    void missingHeadersFallBackToTopicName() {
        var record = new ConsumerRecord<>("payment.declined.DLT", 0, 1L, "k", "garbage");

        service.store(record);

        ArgumentCaptor<FailedEvent> saved = ArgumentCaptor.forClass(FailedEvent.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getOriginalTopic()).isEqualTo("payment.declined");
        assertThat(saved.getValue().getError()).isEqualTo("UnknownException: no message");
    }

    @Test
    void replayRepublishesToOriginalTopic() {
        FailedEvent f = FailedEvent.of("payment.authorized.DLT", "payment.authorized", "ledger-service", "pay-1",
                "{}", "err", 0, 1L, NOW);
        when(repository.findById(f.getId())).thenReturn(Optional.of(f));
        when(kafka.send("payment.authorized", "pay-1", "{}")).thenReturn(CompletableFuture.completedFuture(null));

        var response = service.replay(f.getId());

        verify(kafka).send("payment.authorized", "pay-1", "{}");
        assertThat(response.replayCount()).isEqualTo(1);
        assertThat(response.replayedAt()).isEqualTo(NOW);
    }

    @Test
    void replayFailureIsReported() {
        FailedEvent f = FailedEvent.of("t.DLT", "t", null, "k", "{}", "err", 0, 1L, NOW);
        when(repository.findById(f.getId())).thenReturn(Optional.of(f));
        when(kafka.send("t", "k", "{}")).thenReturn(CompletableFuture.failedFuture(new RuntimeException("down")));

        assertThatThrownBy(() -> service.replay(f.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(f.getReplayCount()).isZero();
    }

    @Test
    void unknownIdIs404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.replay(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listAndGet() {
        FailedEvent f = FailedEvent.of("t.DLT", "t", null, "k", "{}", "err", 0, 1L, NOW);
        Page<FailedEvent> page = new PageImpl<>(List.of(f), PageRequest.of(0, 20), 1);
        when(repository.findAllByOrderByFailedAtDesc(PageRequest.of(0, 20))).thenReturn(page);
        when(repository.findById(f.getId())).thenReturn(Optional.of(f));

        assertThat(service.list(0, 20).content()).hasSize(1);
        assertThat(service.get(f.getId()).id()).isEqualTo(f.getId());
    }

    @Test
    void longErrorsAreTruncated() {
        assertThat(FailedEventService.truncate("x".repeat(5000))).hasSize(FailedEventService.MAX_ERROR_LENGTH);
        assertThat(FailedEventService.truncate("short")).isEqualTo("short");
    }
}
