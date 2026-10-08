package com.payments.payment.service;

import com.payments.common.events.EventType;
import com.payments.common.events.PaymentEvent;
import com.payments.common.outbox.OutboxWriter;
import com.payments.common.util.CardMasker;
import com.payments.payment.domain.Payment;
import com.payments.payment.domain.PaymentRepository;
import com.payments.payment.dto.PaymentRequest;
import com.payments.payment.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Creates payments. The payment row and its payment.initiated outbox row are written in ONE transaction. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxWriter outboxWriter;
    private final CardFingerprinter cardFingerprinter;
    private final Clock clock;

    @Transactional
    public PaymentResponse create(String idempotencyKey, PaymentRequest request) {
        Instant now = clock.instant();
        Payment payment = Payment.receive(
                UUID.randomUUID(),
                idempotencyKey,
                request.merchantId(),
                CardMasker.mask(request.cardNumber()),
                cardFingerprinter.fingerprint(request.cardNumber()),
                request.expiryMonth(),
                request.expiryYear(),
                request.amount().setScale(2, RoundingMode.HALF_EVEN),
                request.currency(),
                now);

        paymentRepository.saveAndFlush(payment);

        outboxWriter.write(PaymentEvent.create(EventType.PAYMENT_INITIATED, payment.getId(), payment.getMerchantId(),
                payment.getAmount(), payment.getCurrency(), payment.getMaskedCard(), null, null, now));

        log.info("Payment {} RECEIVED merchant={} amount={} {} card={}", payment.getId(), payment.getMerchantId(),
                payment.getAmount(), payment.getCurrency(), payment.getMaskedCard());
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentResponse> findByIdempotencyKey(String idempotencyKey) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey).map(PaymentResponse::from);
    }
}
