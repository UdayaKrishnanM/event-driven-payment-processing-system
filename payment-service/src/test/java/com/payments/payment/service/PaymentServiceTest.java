package com.payments.payment.service;

import com.payments.common.domain.PaymentStatus;
import com.payments.common.events.EventType;
import com.payments.common.events.PaymentEvent;
import com.payments.common.outbox.OutboxWriter;
import com.payments.payment.domain.Payment;
import com.payments.payment.domain.PaymentRepository;
import com.payments.payment.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    PaymentRepository repository;
    @Mock
    OutboxWriter outboxWriter;
    @Mock
    CardFingerprinter fingerprinter;

    Clock clock = Clock.fixed(TestData.NOW, ZoneOffset.UTC);

    PaymentService service() {
        return new PaymentService(repository, outboxWriter, fingerprinter, clock);
    }

    @Test
    void savesPaymentAsReceivedWithMaskedCardAndPublishesInitiated() {
        when(fingerprinter.fingerprint(TestData.VISA_OK)).thenReturn("fp-1");
        when(repository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service().create("key-1", TestData.request());

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(repository).saveAndFlush(saved.capture());
        Payment p = saved.getValue();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(p.getMaskedCard()).isEqualTo("**** 1111");
        assertThat(p.getCardFingerprint()).isEqualTo("fp-1");
        assertThat(p.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(p.getAmount()).isEqualByComparingTo("2499.00");

        ArgumentCaptor<PaymentEvent> event = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(outboxWriter).write(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(EventType.PAYMENT_INITIATED);
        assertThat(event.getValue().paymentId()).isEqualTo(p.getId());
        assertThat(event.getValue().maskedCard()).isEqualTo("**** 1111");

        assertThat(response.status()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(response.paymentId()).isEqualTo(p.getId());
        assertThat(response.toString()).doesNotContain(TestData.VISA_OK);
    }

    @Test
    void doesNotPublishWhenTheSaveFails() {
        when(fingerprinter.fingerprint(TestData.VISA_OK)).thenReturn("fp-1");
        when(repository.saveAndFlush(any(Payment.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service().create("key-1", TestData.request()))
                .isInstanceOf(DataIntegrityViolationException.class);
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void findsByIdempotencyKey() {
        Payment p = TestData.payment();
        when(repository.findByIdempotencyKey("k")).thenReturn(Optional.of(p));

        assertThat(service().findByIdempotencyKey("k").map(r -> r.paymentId())).contains(p.getId());
    }
}
