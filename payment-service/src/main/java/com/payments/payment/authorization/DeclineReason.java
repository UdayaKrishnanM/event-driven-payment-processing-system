package com.payments.payment.authorization;

public enum DeclineReason {
    CARD_EXPIRED,
    INSUFFICIENT_FUNDS,
    LIMIT_EXCEEDED,
    VELOCITY_CHECK_FAILED
}
