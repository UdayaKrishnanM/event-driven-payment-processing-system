package com.payments.common.domain;

/**
 * Thrown when something tries to move a payment to a state that is not allowed from its current state.
 * Kafka consumers treat this as NOT retryable: retrying can never make an illegal move legal.
 */
public class IllegalStateTransitionException extends RuntimeException {

    private final PaymentStatus from;
    private final PaymentStatus to;

    public IllegalStateTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Illegal payment state transition " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public PaymentStatus getFrom() {
        return from;
    }

    public PaymentStatus getTo() {
        return to;
    }
}
