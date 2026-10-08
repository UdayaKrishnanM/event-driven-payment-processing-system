package com.payments.ledger.messaging;

import com.payments.common.events.EventType;
import com.payments.common.events.PaymentEvent;
import com.payments.ledger.service.LedgerService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthorizedPaymentConsumerTest {

    @Test
    void delegatesToLedgerService() {
        var ledger = mock(LedgerService.class);
        var event = PaymentEvent.create(EventType.PAYMENT_AUTHORIZED, UUID.randomUUID(), "M", BigDecimal.ONE, "INR",
                "**** 1111", "A", null, Instant.now());

        new AuthorizedPaymentConsumer(ledger).onAuthorized(event);

        verify(ledger).settle(event);
    }
}
