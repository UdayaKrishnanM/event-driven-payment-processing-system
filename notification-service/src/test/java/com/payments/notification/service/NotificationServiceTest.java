package com.payments.notification.service;

import com.payments.common.dedup.EventDeduplicator;
import com.payments.common.events.EventType;
import com.payments.common.events.PaymentEvent;
import com.payments.notification.domain.Notification;
import com.payments.notification.domain.NotificationRepository;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:15:30Z");

    private EventDeduplicator dedup;
    private NotificationRepository repository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        dedup = mock(EventDeduplicator.class);
        repository = mock(NotificationRepository.class);
        service = new NotificationService(dedup, repository, Clock.fixed(NOW, ZoneOffset.UTC));
        when(dedup.firstDelivery(any())).thenReturn(true);
    }

    private PaymentEvent event(EventType type, String authCode, String reason) {
        return PaymentEvent.create(type, UUID.randomUUID(), "MER-1001", new BigDecimal("2499.00"), "INR",
                "**** 1111", authCode, reason, NOW);
    }

    @Test
    void savesNotificationForAuthorized() {
        var e = event(EventType.PAYMENT_AUTHORIZED, "A1B2C3", null);

        service.record(e);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo("PAYMENT_AUTHORIZED");
        assertThat(saved.getValue().getMerchantId()).isEqualTo("MER-1001");
        assertThat(saved.getValue().getMessage()).contains("authorized", "A1B2C3", "INR 2499.00", "**** 1111");
    }

    @Test
    void messagesForEachOutcome() {
        assertThat(NotificationService.messageFor(event(EventType.PAYMENT_DECLINED, null, "INSUFFICIENT_FUNDS")))
                .contains("declined", "INSUFFICIENT_FUNDS");
        assertThat(NotificationService.messageFor(event(EventType.PAYMENT_SETTLED, "A1B2C3", null)))
                .contains("settled");
        assertThatThrownBy(() -> NotificationService.messageFor(event(EventType.PAYMENT_INITIATED, null, null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void duplicateIsSkipped() {
        var e = event(EventType.PAYMENT_SETTLED, null, null);
        when(dedup.firstDelivery(e.eventId())).thenReturn(false);

        service.record(e);

        verify(repository, never()).save(any());
    }

    @Test
    void incompleteEventIsRejected() {
        var e = new PaymentEvent(UUID.randomUUID(), null, UUID.randomUUID(), "M", BigDecimal.ONE, "INR", null, null,
                null, NOW, 1);
        assertThatThrownBy(() -> service.record(e)).isInstanceOf(ValidationException.class);
    }

    @Test
    void listFiltersByPaymentThenMerchantThenAll() {
        UUID paymentId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 100);
        when(repository.findByPaymentIdOrderByCreatedAtDesc(paymentId, pageable)).thenReturn(Page.empty(pageable));
        when(repository.findByMerchantIdOrderByCreatedAtDesc("MER-1", pageable)).thenReturn(Page.empty(pageable));
        when(repository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(Page.empty(pageable));

        service.list("MER-1", paymentId, -1, 1000);
        service.list("MER-1", null, 0, 100);
        service.list(" ", null, 0, 100);

        verify(repository).findByPaymentIdOrderByCreatedAtDesc(paymentId, pageable);
        verify(repository).findByMerchantIdOrderByCreatedAtDesc("MER-1", pageable);
        verify(repository).findAllByOrderByCreatedAtDesc(pageable);
    }
}
