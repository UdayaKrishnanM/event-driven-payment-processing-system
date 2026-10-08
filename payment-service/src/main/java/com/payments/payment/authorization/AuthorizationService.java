package com.payments.payment.authorization;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Simulated card issuer. Plain Java (no Spring annotations) so it is trivial to unit test.
 * Runs the injected rules in order; the first decline wins, otherwise approves with a 6-character auth code.
 */
public class AuthorizationService {

    private final List<AuthorizationRule> rules;
    private final Supplier<String> authCodeGenerator;

    public AuthorizationService(List<AuthorizationRule> rules, Supplier<String> authCodeGenerator) {
        this.rules = List.copyOf(rules);
        this.authCodeGenerator = authCodeGenerator;
    }

    public AuthorizationResult authorize(AuthorizationRequest request) {
        for (AuthorizationRule rule : rules) {
            Optional<DeclineReason> decline = rule.evaluate(request);
            if (decline.isPresent()) {
                return AuthorizationResult.decline(decline.get());
            }
        }
        return AuthorizationResult.approve(authCodeGenerator.get());
    }
}
