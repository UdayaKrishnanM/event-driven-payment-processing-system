package com.payments.ledger.service;

import com.payments.common.dedup.EventDeduplicator;
import com.payments.common.events.EventType;
import com.payments.common.events.PaymentEvent;
import com.payments.common.outbox.OutboxWriter;
import com.payments.ledger.chaos.ChaosSettings;
import com.payments.ledger.chaos.SimulatedLedgerFailureException;
import com.payments.ledger.domain.Accounts;
import com.payments.ledger.domain.Direction;
import com.payments.ledger.domain.LedgerEntry;
import com.payments.ledger.domain.LedgerEntryRepository;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LedgerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:15:30Z");

    private EventDeduplicator dedup;
    private LedgerEntryRepository repository;
    private OutboxWriter outbox;
    private ChaosSettings chaos;
    private LedgerService service;
    private PaymentEvent authorized;

    @BeforeEach
    void setUp() {
        dedup = mock(EventDeduplicator.class);
        repository = mock(LedgerEntryRepository.class);
        outbox = mock(OutboxWriter.class);
        chaos = new ChaosSettings("");
        service = new LedgerService(dedup, repository, outbox, chaos, Clock.fixed(NOW, ZoneOffset.UTC));
        authorized = PaymentEvent.create(EventType.PAYMENT_AUTHORIZED, UUID.randomUUID(), "MER-1001",
                new BigDecimal("2499.00"), "INR", "**** 1111", "A1B2C3", null, NOW.minusSeconds(1));
        when(dedup.firstDelivery(authorized.eventId())).thenReturn(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void postsBalancedDebitAndCreditAndPublishesSettled() {
        service.settle(authorized);

        ArgumentCaptor<List<LedgerEntry>> entries = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(entries.capture());
        assertThat(entries.getValue()).hasSize(2);
        LedgerEntry debit = entries.getValue().get(0);
        LedgerEntry credit = entries.getValue().get(1);
        assertThat(debit.getDirection()).isEqualTo(Direction.DEBIT);
        assertThat(debit.getAccount()).isEqualTo(Accounts.CUSTOMER_CLEARING);
        assertThat(credit.getDirection()).isEqualTo(Direction.CREDIT);
        assertThat(credit.getAccount()).isEqualTo("MERCHANT:MER-1001");
        assertThat(debit.getAmount()).isEqualByComparingTo(credit.getAmount()).isEqualByComparingTo("2499.00");
        assertThat(LedgerQueryService.isBalanced(entries.getValue())).isTrue();

        ArgumentCaptor<PaymentEvent> settled = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(outbox).write(settled.capture());
        assertThat(settled.getValue().eventType()).isEqualTo(EventType.PAYMENT_SETTLED);
        assertThat(settled.getValue().paymentId()).isEqualTo(authorized.paymentId());
        assertThat(settled.getValue().eventId()).isNotEqualTo(authorized.eventId());
    }

    @Test
    void duplicateEventPostsNothing() {
        when(dedup.firstDelivery(authorized.eventId())).thenReturn(false);

        service.settle(authorized);

        verifyNoInteractions(repository, outbox);
    }

    @Test
    void paymentAlreadyPostedByAnotherEventIsSkipped() {
        when(repository.existsByPaymentId(authorized.paymentId())).thenReturn(true);

        service.settle(authorized);

        verify(repository, never()).saveAll(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void chaosModeThrowsARetryableFailure() {
        chaos.setFailMerchantId("MER-1001");

        assertThatThrownBy(() -> service.settle(authorized)).isInstanceOf(SimulatedLedgerFailureException.class);
        verifyNoInteractions(repository, outbox);
    }

    @Test
    void badEventsAreRejectedAsNonRetryable() {
        var noAmount = new PaymentEvent(UUID.randomUUID(), EventType.PAYMENT_AUTHORIZED, UUID.randomUUID(), "MER-1",
                null, "INR", "**** 1111", "A", null, NOW, 1);
        var zeroAmount = new PaymentEvent(UUID.randomUUID(), EventType.PAYMENT_AUTHORIZED, UUID.randomUUID(), "MER-1",
                BigDecimal.ZERO, "INR", "**** 1111", "A", null, NOW, 1);

        assertThatThrownBy(() -> service.settle(noAmount)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.settle(zeroAmount)).isInstanceOf(ValidationException.class);
    }
}
