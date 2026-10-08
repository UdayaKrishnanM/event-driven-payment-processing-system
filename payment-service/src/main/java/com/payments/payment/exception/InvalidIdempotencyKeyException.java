package com.payments.payment.exception;

public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException() {
        super("Idempotency-Key header must be a UUID, e.g. 3f0c8a52-7d1e-4e7b-9a43-1b2c3d4e5f60");
    }
}
