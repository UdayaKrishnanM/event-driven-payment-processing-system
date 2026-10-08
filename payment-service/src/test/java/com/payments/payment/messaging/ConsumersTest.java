package com.payments.payment.messaging;

import com.payments.common.events.EventType;
import com.payments.common.events.PaymentEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Listeners are thin: they only delegate. */
class ConsumersTest {

    private final PaymentEvent event = PaymentEvent.create(EventType.PAYMENT_INITIATED, UUID.randomUUID(), "MER-1",
            BigDecimal.ONE, "INR", "**** 1111", null, null, Instant.now());

    @Test
    void initiatedConsumerDelegates() {
        var processor = mock(AuthorizationProcessor.class);
        new PaymentInitiatedConsumer(processor).onInitiated(event);
        verify(processor).process(event);
    }

    @Test
    void settledConsumerDelegates() {
        var processor = mock(SettlementProcessor.class);
        new PaymentSettledConsumer(processor).onSettled(event);
        verify(processor).process(event);
    }
}
