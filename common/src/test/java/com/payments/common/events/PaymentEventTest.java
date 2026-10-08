package com.payments.common.events;

import com.payments.common.kafka.Topics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEventTest {

    private final UUID paymentId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-20T10:15:30Z");

    @Test
    void createAssignsFreshIdAndCurrentSchemaVersion() {
        PaymentEvent e = PaymentEvent.create(EventType.PAYMENT_AUTHORIZED, paymentId, "MER-1001",
                new BigDecimal("2499.00"), "INR", "**** 1111", "A1B2C3", null, now);

        assertThat(e.eventId()).isNotNull();
        assertThat(e.schemaVersion()).isEqualTo(PaymentEvent.CURRENT_SCHEMA_VERSION);
        assertThat(e.key()).isEqualTo(paymentId.toString());
        assertThat(e.occurredAt()).isEqualTo(now);
    }

    @Test
    void nextKeepsPaymentDataButChangesIdTypeAndTime() {
        PaymentEvent authorized = PaymentEvent.create(EventType.PAYMENT_AUTHORIZED, paymentId, "MER-1001",
                new BigDecimal("10.00"), "USD", "**** 1111", "A1B2C3", null, now);
        Instant later = now.plusSeconds(1);

        PaymentEvent settled = authorized.next(EventType.PAYMENT_SETTLED, later);

        assertThat(settled.eventId()).isNotEqualTo(authorized.eventId());
        assertThat(settled.eventType()).isEqualTo(EventType.PAYMENT_SETTLED);
        assertThat(settled.paymentId()).isEqualTo(paymentId);
        assertThat(settled.amount()).isEqualByComparingTo("10.00");
        assertThat(settled.authCode()).isEqualTo("A1B2C3");
        assertThat(settled.occurredAt()).isEqualTo(later);
    }

    @Test
    void everyEventTypeMapsToItsTopic() {
        assertThat(EventType.PAYMENT_INITIATED.topic()).isEqualTo(Topics.PAYMENT_INITIATED);
        assertThat(EventType.PAYMENT_AUTHORIZED.topic()).isEqualTo(Topics.PAYMENT_AUTHORIZED);
        assertThat(EventType.PAYMENT_DECLINED.topic()).isEqualTo(Topics.PAYMENT_DECLINED);
        assertThat(EventType.PAYMENT_SETTLED.topic()).isEqualTo(Topics.PAYMENT_SETTLED);
    }
}
