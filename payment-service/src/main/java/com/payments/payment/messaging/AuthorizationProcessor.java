package com.payments.payment.messaging;

import com.payments.common.dedup.EventDeduplicator;
import com.payments.common.events.EventType;
import com.payments.common.events.PaymentEvent;
import com.payments.common.outbox.OutboxWriter;
import com.payments.payment.authorization.AuthorizationRequest;
import com.payments.payment.authorization.AuthorizationResult;
import com.payments.payment.authorization.AuthorizationService;
import com.payments.payment.domain.Payment;
import com.payments.payment.domain.PaymentRepository;
import com.payments.payment.exception.PaymentNotFoundException;
import com.payments.payment.service.PaymentStatusCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * payment.initiated -> authorize -> payment.authorized | payment.declined.
 * One transaction: de-dup marker + status change + outbox row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationProcessor {

    private final EventDeduplicator deduplicator;
    private final PaymentRepository paymentRepository;
    private final AuthorizationService authorizationService;
    private final OutboxWriter outboxWriter;
    private final PaymentStatusCache statusCache;
    private final Clock clock;

    @Transactional
    public void process(PaymentEvent event) {
        if (!deduplicator.firstDelivery(event.eventId())) {
            return;
        }
        Payment payment = paymentRepository.findById(event.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException(event.paymentId()));

        AuthorizationResult result = authorizationService.authorize(new AuthorizationRequest(
                payment.getId(), payment.getAmount(), payment.last4(), payment.getExpiryMonth(),
                payment.getExpiryYear(), payment.getCardFingerprint()));

        Instant now = clock.instant();
        PaymentEvent outcome;
        if (result.approved()) {
            payment.authorize(result.authCode(), now);
            outcome = PaymentEvent.create(EventType.PAYMENT_AUTHORIZED, payment.getId(), payment.getMerchantId(),
                    payment.getAmount(), payment.getCurrency(), payment.getMaskedCard(), result.authCode(), null, now);
            log.info("Payment {} AUTHORIZED authCode={}", payment.getId(), result.authCode());
        } else {
            String reason = result.declineReason().name();
            payment.decline(reason, now);
            outcome = PaymentEvent.create(EventType.PAYMENT_DECLINED, payment.getId(), payment.getMerchantId(),
                    payment.getAmount(), payment.getCurrency(), payment.getMaskedCard(), null, reason, now);
            log.info("Payment {} DECLINED reason={}", payment.getId(), reason);
        }
        outboxWriter.write(outcome);
        statusCache.evictAfterCommit(payment.getId());
    }
}
