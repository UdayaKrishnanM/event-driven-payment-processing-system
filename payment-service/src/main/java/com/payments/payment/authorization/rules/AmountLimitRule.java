package com.payments.payment.authorization.rules;

import com.payments.payment.authorization.AuthorizationRequest;
import com.payments.payment.authorization.AuthorizationRule;
import com.payments.payment.authorization.DeclineReason;

import java.math.BigDecimal;
import java.util.Optional;

/** Rule 3: decline LIMIT_EXCEEDED if the amount is over the per-transaction limit (50000 by default). */
public class AmountLimitRule implements AuthorizationRule {

    private final BigDecimal maxAmount;

    public AmountLimitRule(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    @Override
    public Optional<DeclineReason> evaluate(AuthorizationRequest request) {
        return request.amount().compareTo(maxAmount) > 0
                ? Optional.of(DeclineReason.LIMIT_EXCEEDED) : Optional.empty();
    }
}
