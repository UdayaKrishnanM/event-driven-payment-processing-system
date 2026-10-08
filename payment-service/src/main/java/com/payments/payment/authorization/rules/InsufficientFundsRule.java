package com.payments.payment.authorization.rules;

import com.payments.payment.authorization.AuthorizationRequest;
import com.payments.payment.authorization.AuthorizationRule;
import com.payments.payment.authorization.DeclineReason;

import java.util.Optional;
import java.util.Set;

/** Rule 2: test cards ending in 0002 always decline with INSUFFICIENT_FUNDS. */
public class InsufficientFundsRule implements AuthorizationRule {

    private final Set<String> decliningLast4;

    public InsufficientFundsRule(Set<String> decliningLast4) {
        this.decliningLast4 = Set.copyOf(decliningLast4);
    }

    @Override
    public Optional<DeclineReason> evaluate(AuthorizationRequest request) {
        return decliningLast4.contains(request.cardLast4())
                ? Optional.of(DeclineReason.INSUFFICIENT_FUNDS) : Optional.empty();
    }
}
