package com.payments.payment.authorization.rules;

import com.payments.payment.authorization.AuthorizationRequest;
import com.payments.payment.authorization.AuthorizationRule;
import com.payments.payment.authorization.DeclineReason;

import java.time.Clock;
import java.time.YearMonth;
import java.util.Optional;

/** Rule 1: decline CARD_EXPIRED if the card's expiry month has passed. A card is valid through its expiry month. */
public class ExpiredCardRule implements AuthorizationRule {

    private final Clock clock;

    public ExpiredCardRule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<DeclineReason> evaluate(AuthorizationRequest request) {
        YearMonth expiry = YearMonth.of(request.expiryYear(), request.expiryMonth());
        return expiry.isBefore(YearMonth.now(clock)) ? Optional.of(DeclineReason.CARD_EXPIRED) : Optional.empty();
    }
}
