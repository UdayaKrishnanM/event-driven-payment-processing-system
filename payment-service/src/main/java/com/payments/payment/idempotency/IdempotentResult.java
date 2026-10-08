package com.payments.payment.idempotency;

/** The response plus whether it was replayed from the idempotency store. */
public record IdempotentResult<T>(T body, boolean replayed) {
}
