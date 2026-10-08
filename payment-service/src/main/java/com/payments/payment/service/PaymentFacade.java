package com.payments.payment.service;

import com.payments.payment.dto.PaymentRequest;
import com.payments.payment.dto.PaymentResponse;
import com.payments.payment.exception.InvalidIdempotencyKeyException;
import com.payments.payment.idempotency.IdempotencyService;
import com.payments.payment.idempotency.IdempotentResult;
import com.payments.payment.idempotency.RequestHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Entry point for creating a payment: Redis idempotency first (layer 1), then the transactional create.
 * If Redis lost the key (flushed / expired) the DB unique constraint on idempotency_key still blocks a duplicate
 * and we return the payment that already exists (layer 2, the backstop).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final IdempotencyService idempotencyService;
    private final PaymentService paymentService;

    public IdempotentResult<PaymentResponse> create(String idempotencyKey, PaymentRequest request) {
        String key = normalizeKey(idempotencyKey);
        String requestHash = RequestHasher.sha256(request.canonicalForm());
        return idempotencyService.execute(key, requestHash, PaymentResponse.class, () -> createOnce(key, request));
    }

    private PaymentResponse createOnce(String key, PaymentRequest request) {
        try {
            return paymentService.create(key, request);
        } catch (DataIntegrityViolationException e) {
            log.warn("Idempotency-Key {} already exists in the database; returning the existing payment", key);
            return paymentService.findByIdempotencyKey(key).orElseThrow(() -> e);
        }
    }

    static String normalizeKey(String idempotencyKey) {
        try {
            return UUID.fromString(idempotencyKey.trim()).toString();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidIdempotencyKeyException();
        }
    }
}
