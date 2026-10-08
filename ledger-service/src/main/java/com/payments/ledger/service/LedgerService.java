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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Settles an authorized payment. In ONE transaction:
 * <ol>
 *   <li>record the eventId in processed_events (skip if already there),</li>
 *   <li>DEBIT CUSTOMER_CLEARING and CREDIT MERCHANT:&lt;id&gt; for the same amount,</li>
 *   <li>write payment.settled to the outbox.</li>
 * </ol>
 * The UNIQUE (payment_id, account, direction) constraint is a second guard against double posting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final EventDeduplicator deduplicator;
    private final LedgerEntryRepository entryRepository;
    private final OutboxWriter outboxWriter;
    private final ChaosSettings chaosSettings;
    private final Clock clock;

    @Transactional
    public void settle(PaymentEvent event) {
        validate(event);
        if (chaosSettings.shouldFail(event.merchantId())) {
            throw new SimulatedLedgerFailureException(event.merchantId());
        }
        if (!deduplicator.firstDelivery(event.eventId())) {
            return;
        }
        if (entryRepository.existsByPaymentId(event.paymentId())) {
            log.warn("Payment {} already posted by another event; skipping", event.paymentId());
            return;
        }
        Instant now = clock.instant();
        entryRepository.saveAll(List.of(
                LedgerEntry.of(event.paymentId(), Accounts.CUSTOMER_CLEARING, Direction.DEBIT, event.amount(),
                        event.currency(), now),
                LedgerEntry.of(event.paymentId(), Accounts.merchant(event.merchantId()), Direction.CREDIT,
                        event.amount(), event.currency(), now)));

        outboxWriter.write(event.next(EventType.PAYMENT_SETTLED, now));
        log.info("Payment {} posted to ledger: DEBIT {} / CREDIT {} {} {}", event.paymentId(),
                Accounts.CUSTOMER_CLEARING, Accounts.merchant(event.merchantId()), event.amount(), event.currency());
    }

    /** Bad data can never succeed, so it is not retried (ValidationException goes straight to the DLT). */
    static void validate(PaymentEvent event) {
        if (event.eventId() == null || event.paymentId() == null || event.merchantId() == null
                || event.amount() == null || event.currency() == null) {
            throw new ValidationException("Authorized event is missing required fields: " + event);
        }
        if (event.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Authorized event has a non-positive amount: " + event.amount());
        }
    }
}
