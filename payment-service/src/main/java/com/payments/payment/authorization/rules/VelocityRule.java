package com.payments.payment.authorization.rules;

import com.payments.payment.authorization.AuthorizationRequest;
import com.payments.payment.authorization.AuthorizationRule;
import com.payments.payment.authorization.DeclineReason;
import com.payments.payment.authorization.VelocityCounter;

import java.util.Optional;

/** Rule 4: decline VELOCITY_CHECK_FAILED if the same card makes more than 5 payments in 60 seconds. */
public class VelocityRule implements AuthorizationRule {

    private final VelocityCounter counter;
    private final int maxPaymentsPerWindow;

    public VelocityRule(VelocityCounter counter, int maxPaymentsPerWindow) {
        this.counter = counter;
        this.maxPaymentsPerWindow = maxPaymentsPerWindow;
    }

    @Override
    public Optional<DeclineReason> evaluate(AuthorizationRequest request) {
        long count = counter.increment(request.cardFingerprint());
        return count > maxPaymentsPerWindow ? Optional.of(DeclineReason.VELOCITY_CHECK_FAILED) : Optional.empty();
    }
}
