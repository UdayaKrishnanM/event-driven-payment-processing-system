package com.payments.common.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class OutboxPublisherTest {

    private JdbcTemplate jdbc;
    private KafkaTemplate<String, Object> kafka;
    private PlatformTransactionManager txManager;
    private OutboxPublisher publisher;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        kafka = mock(KafkaTemplate.class);
        txManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);
        publisher = new OutboxPublisher(jdbc, kafka, new TransactionTemplate(txManager), 500, 1000L);
    }

    private void outboxContainsOneRow() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(id);
        when(rs.getString("topic")).thenReturn("payment.initiated");
        when(rs.getString("event_key")).thenReturn("pay-1");
        when(rs.getString("payload")).thenReturn("{}");
        doAnswer(inv -> {
            RowMapper<OutboxMessage> mapper = inv.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbc).query(eq(OutboxPublisher.SELECT_SQL), any(RowMapper.class), eq(500));
    }

    @Test
    void sendsUnsentRowsAndMarksThemSent() throws Exception {
        outboxContainsOneRow();
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        int sent = publisher.publishBatch();

        assertThat(sent).isEqualTo(1);
        verify(kafka).send("payment.initiated", "pay-1", "{}");
        verify(jdbc).batchUpdate(eq(OutboxPublisher.MARK_SENT_SQL), anyList());
        verify(txManager).commit(any());
    }

    @Test
    void emptyOutboxSendsNothing() {
        when(jdbc.query(eq(OutboxPublisher.SELECT_SQL), any(RowMapper.class), eq(500))).thenReturn(List.of());

        assertThat(publisher.publishBatch()).isZero();
        verify(kafka, never()).send(anyString(), anyString(), any());
    }

    @Test
    void kafkaFailureRollsBackSoRowsAreRetried() throws Exception {
        outboxContainsOneRow();
        when(kafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThatThrownBy(() -> publisher.publishBatch()).isInstanceOf(IllegalStateException.class);
        verify(jdbc, never()).batchUpdate(anyString(), anyList());
        verify(txManager).rollback(any());
    }

    @Test
    void scheduledRunSwallowsErrorsSoTheSchedulerKeepsRunning() throws Exception {
        outboxContainsOneRow();
        when(kafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThatCode(() -> publisher.publishPending()).doesNotThrowAnyException();
    }

    @Test
    void scheduledRunPublishesPending() throws Exception {
        outboxContainsOneRow();
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        verify(kafka).send("payment.initiated", "pay-1", "{}");
    }

    @Test
    void cleanupDeletesOldSentRows() {
        when(jdbc.update(OutboxPublisher.CLEANUP_SQL)).thenReturn(3);

        publisher.cleanup();

        verify(jdbc).update(OutboxPublisher.CLEANUP_SQL);
    }
}
