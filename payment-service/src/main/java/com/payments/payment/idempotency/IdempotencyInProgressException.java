package com.payments.payment.idempotency;

/** 409: a request with the same Idempotency-Key is still being processed. */
public class IdempotencyInProgressException extends RuntimeException {

    public IdempotencyInProgressException(String key) {
        super("A request with Idempotency-Key " + key + " is still being processed. Retry shortly.");
    }
}
