package com.payments.payment.messaging;

import com.payments.common.dedup.EventDeduplicator;
import com.payments.common.domain.IllegalStateTransitionException;
import com.payments.common.domain.PaymentStatus;
import com.payments.common.events.EventType;
import com.payments.common.events.PaymentEvent;
import com.payments.common.outbox.OutboxWriter;
import com.payments.payment.authorization.AuthorizationResult;
import com.payments.payment.authorization.AuthorizationService;
import com.payments.payment.authorization.DeclineReason;
import com.payments.payment.domain.Payment;
import com.payments.payment.domain.PaymentRepository;
import com.payments.payment.exception.PaymentNotFoundException;
import com.payments.payment.service.PaymentStatusCache;
import com.payments.payment.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthorizationProcessorTest {

    private EventDeduplicator dedup;
    private PaymentRepository repository;
    private AuthorizationService authorizationService;
    private OutboxWriter outbox;
    private PaymentStatusCache cache;
    private AuthorizationProcessor processor;
    private Payment payment;
    private PaymentEvent initiated;

    @BeforeEach
    void setUp() {
        dedup = mock(EventDeduplicator.class);
        repository = mock(PaymentRepository.class);
        authorizationService = mock(AuthorizationService.class);
        outbox = mock(OutboxWriter.class);
        cache = mock(PaymentStatusCache.class);
        processor = new AuthorizationProcessor(dedup, repository, authorizationService, outbox, cache,
                Clock.fixed(TestData.NOW, ZoneOffset.UTC));
        payment = TestData.payment();
        initiated = PaymentEvent.create(EventType.PAYMENT_INITIATED, payment.getId(), payment.getMerchantId(),
                payment.getAmount(), payment.getCurrency(), payment.getMaskedCard(), null, null, TestData.NOW);
        when(dedup.firstDelivery(initiated.eventId())).thenReturn(true);
        when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));
    }

    @Test
    void approvedPaymentBecomesAuthorizedAndPublishesAuthorized() {
        when(authorizationService.authorize(any())).thenReturn(AuthorizationResult.approve("A1B2C3"));

        processor.process(initiated);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        ArgumentCaptor<PaymentEvent> out = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(outbox).write(out.capture());
        assertThat(out.getValue().eventType()).isEqualTo(EventType.PAYMENT_AUTHORIZED);
        assertThat(out.getValue().authCode()).isEqualTo("A1B2C3");
        verify(cache).evictAfterCommit(payment.getId());
    }

    @Test
    void declinedPaymentPublishesDeclinedWithReason() {
        when(authorizationService.authorize(any())).thenReturn(AuthorizationResult.decline(DeclineReason.INSUFFICIENT_FUNDS));

        processor.process(initiated);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        ArgumentCaptor<PaymentEvent> out = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(outbox).write(out.capture());
        assertThat(out.getValue().eventType()).isEqualTo(EventType.PAYMENT_DECLINED);
        assertThat(out.getValue().declineReason()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void duplicateEventIsSkipped() {
        when(dedup.firstDelivery(initiated.eventId())).thenReturn(false);

        processor.process(initiated);

        verifyNoInteractions(repository, authorizationService, outbox);
    }

    @Test
    void unknownPaymentFails() {
        when(repository.findById(payment.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.process(initiated)).isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void alreadyFinalPaymentIsAnIllegalTransition() {
        payment.decline("LIMIT_EXCEEDED", TestData.NOW);
        when(authorizationService.authorize(any())).thenReturn(AuthorizationResult.approve("A1B2C3"));

        assertThatThrownBy(() -> processor.process(initiated)).isInstanceOf(IllegalStateTransitionException.class);
        verifyNoInteractions(outbox);
    }
}
