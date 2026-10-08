package com.payments.common.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Payment lifecycle: RECEIVED -> AUTHORIZED -> SETTLED, or RECEIVED -> DECLINED.
 * SETTLED and DECLINED are final. The allowed moves live here so that a late or duplicate
 * event can never move a payment backwards.
 */
public enum PaymentStatus {
    RECEIVED,
    AUTHORIZED,
    SETTLED,
    DECLINED;

    public Set<PaymentStatus> allowedNext() {
        return switch (this) {
            case RECEIVED -> EnumSet.of(AUTHORIZED, DECLINED);
            case AUTHORIZED -> EnumSet.of(SETTLED);
            case SETTLED, DECLINED -> EnumSet.noneOf(PaymentStatus.class);
        };
    }

    public boolean canMoveTo(PaymentStatus next) {
        return next != null && allowedNext().contains(next);
    }

    public boolean isFinal() {
        return allowedNext().isEmpty();
    }

    /** Returns {@code next} if the move is legal, otherwise throws {@link IllegalStateTransitionException}. */
    public PaymentStatus transitionTo(PaymentStatus next) {
        if (!canMoveTo(next)) {
            throw new IllegalStateTransitionException(this, next);
        }
        return next;
    }
}
