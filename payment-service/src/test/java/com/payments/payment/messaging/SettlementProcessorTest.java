package com.payments.payment.messaging;

import com.payments.common.dedup.EventDeduplicator;
import com.payments.common.domain.PaymentStatus;
import com.payments.common.events.EventType;
import com.payments.common.events.PaymentEvent;
import com.payments.payment.domain.Payment;
import com.payments.payment.domain.PaymentRepository;
import com.payments.payment.exception.PaymentNotFoundException;
import com.payments.payment.service.PaymentStatusCache;
import com.payments.payment.support.TestData;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SettlementProcessorTest {

    private EventDeduplicator dedup;
    private PaymentRepository repository;
    private PaymentStatusCache cache;
    private SimpleMeterRegistry registry;
    private SettlementProcessor processor;
    private Payment payment;
    private PaymentEvent settled;

    @BeforeEach
    void setUp() {
        dedup = mock(EventDeduplicator.class);
        repository = mock(PaymentRepository.class);
        cache = mock(PaymentStatusCache.class);
        registry = new SimpleMeterRegistry();
        processor = new SettlementProcessor(dedup, repository, cache,
                Clock.fixed(TestData.NOW.plusSeconds(3), ZoneOffset.UTC), registry);
        payment = TestData.payment();
        payment.authorize("A1B2C3", TestData.NOW.plusSeconds(1));
        settled = PaymentEvent.create(EventType.PAYMENT_SETTLED, payment.getId(), payment.getMerchantId(),
                payment.getAmount(), payment.getCurrency(), payment.getMaskedCard(), "A1B2C3", null, TestData.NOW);
        when(dedup.firstDelivery(settled.eventId())).thenReturn(true);
        when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));
    }

    @Test
    void marksSettledAndRecordsEndToEndTime() {
        processor.process(settled);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SETTLED);
        verify(cache).evictAfterCommit(payment.getId());
        var timer = registry.get("payments.settlement.duration").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(3.0);
    }

    @Test
    void duplicateIsSkipped() {
        when(dedup.firstDelivery(settled.eventId())).thenReturn(false);

        processor.process(settled);

        verifyNoInteractions(repository);
    }

    @Test
    void unknownPaymentFails() {
        when(repository.findById(payment.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.process(settled)).isInstanceOf(PaymentNotFoundException.class);
    }
}
