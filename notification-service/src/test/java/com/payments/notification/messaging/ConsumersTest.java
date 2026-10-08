package com.payments.notification.messaging;

import com.payments.common.events.EventType;
import com.payments.common.events.PaymentEvent;
import com.payments.notification.service.FailedEventService;
import com.payments.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConsumersTest {

    @Test
    void outcomeConsumerDelegates() {
        var service = mock(NotificationService.class);
        var event = PaymentEvent.create(EventType.PAYMENT_SETTLED, UUID.randomUUID(), "M", BigDecimal.ONE, "INR",
                "**** 1111", null, null, Instant.now());

        new PaymentOutcomeConsumer(service).onOutcome(event);

        verify(service).record(event);
    }

    @Test
    void dltMonitorDelegates() {
        var service = mock(FailedEventService.class);
        var record = new ConsumerRecord<>("payment.settled.DLT", 0, 0L, "k", "v");

        new DeadLetterMonitor(service).onDeadLetter(record);

        verify(service).store(record);
    }
}
