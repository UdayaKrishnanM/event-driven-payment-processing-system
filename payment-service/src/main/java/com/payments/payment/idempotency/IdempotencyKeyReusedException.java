package com.payments.payment.idempotency;

/** 422: the same Idempotency-Key was sent with a different request body. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("Idempotency-Key " + key + " was already used for a different request");
    }
}
