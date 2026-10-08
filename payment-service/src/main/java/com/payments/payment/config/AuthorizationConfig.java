package com.payments.payment.config;

import com.payments.payment.authorization.AuthCodeGenerator;
import com.payments.payment.authorization.AuthorizationRule;
import com.payments.payment.authorization.AuthorizationService;
import com.payments.payment.authorization.VelocityCounter;
import com.payments.payment.authorization.rules.AmountLimitRule;
import com.payments.payment.authorization.rules.ExpiredCardRule;
import com.payments.payment.authorization.rules.InsufficientFundsRule;
import com.payments.payment.authorization.rules.VelocityRule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Set;

/**
 * Wires the simulated issuer. Rules are evaluated in this order; the first one that declines wins.
 * Adding a new rule = adding a class + one line here; AuthorizationService never changes (open/closed principle).
 */
@Configuration
public class AuthorizationConfig {

    @Bean
    public AuthorizationService authorizationService(
            Clock clock,
            VelocityCounter velocityCounter,
            @Value("${payments.authorization.insufficient-funds-last4:0002}") Set<String> insufficientFundsLast4,
            @Value("${payments.authorization.max-amount:50000}") BigDecimal maxAmount,
            @Value("${payments.authorization.velocity.max-payments:5}") int maxPayments) {
        List<AuthorizationRule> rules = List.of(
                new ExpiredCardRule(clock),
                new InsufficientFundsRule(insufficientFundsLast4),
                new AmountLimitRule(maxAmount),
                new VelocityRule(velocityCounter, maxPayments));
        return new AuthorizationService(rules, new AuthCodeGenerator());
    }
}
