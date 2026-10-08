package com.payments.payment.messaging;

import com.payments.common.dedup.EventDeduplicator;
import com.payments.common.events.PaymentEvent;
import com.payments.payment.domain.Payment;
import com.payments.payment.domain.PaymentRepository;
import com.payments.payment.exception.PaymentNotFoundException;
import com.payments.payment.service.PaymentStatusCache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * payment.settled -> payment SETTLED. Also records the end-to-end time from RECEIVED to SETTLED
 * as the metric {@code payments.settlement.duration} (see /actuator/prometheus).
 */
@Slf4j
@Service
public class SettlementProcessor {

    private final EventDeduplicator deduplicator;
    private final PaymentRepository paymentRepository;
    private final PaymentStatusCache statusCache;
    private final Clock clock;
    private final Timer settlementTimer;

    public SettlementProcessor(EventDeduplicator deduplicator, PaymentRepository paymentRepository,
                               PaymentStatusCache statusCache, Clock clock, MeterRegistry meterRegistry) {
        this.deduplicator = deduplicator;
        this.paymentRepository = paymentRepository;
        this.statusCache = statusCache;
        this.clock = clock;
        this.settlementTimer = Timer.builder("payments.settlement.duration")
                .description("Time from payment RECEIVED to SETTLED")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    @Transactional
    public void process(PaymentEvent event) {
        if (!deduplicator.firstDelivery(event.eventId())) {
            return;
        }
        Payment payment = paymentRepository.findById(event.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException(event.paymentId()));
        Instant now = clock.instant();
        payment.settle(now);
        statusCache.evictAfterCommit(payment.getId());
        Duration endToEnd = Duration.between(payment.getCreatedAt(), now);
        settlementTimer.record(endToEnd);
        log.info("Payment {} SETTLED in {} ms end-to-end", payment.getId(), endToEnd.toMillis());
    }
}
