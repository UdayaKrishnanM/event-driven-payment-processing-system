package com.payments.payment.authorization;

import java.util.Optional;

/** One issuer rule. Returns a decline reason, or empty to let the payment through to the next rule. */
public interface AuthorizationRule {

    Optional<DeclineReason> evaluate(AuthorizationRequest request);
}
