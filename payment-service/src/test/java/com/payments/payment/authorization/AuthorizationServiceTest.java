package com.payments.payment.authorization;

import com.payments.payment.authorization.rules.AmountLimitRule;
import com.payments.payment.authorization.rules.ExpiredCardRule;
import com.payments.payment.authorization.rules.InsufficientFundsRule;
import com.payments.payment.authorization.rules.VelocityRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** One test case per issuer rule, plus the approval path. */
class AuthorizationServiceTest {

    /** "Today" is October 2026. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC);

    private static AuthorizationService service(long velocityCount) {
        VelocityCounter counter = fp -> velocityCount;
        return new AuthorizationService(List.of(
                new ExpiredCardRule(CLOCK),
                new InsufficientFundsRule(Set.of("0002")),
                new AmountLimitRule(new BigDecimal("50000")),
                new VelocityRule(counter, 5)), () -> "A1B2C3");
    }

    private static AuthorizationRequest req(String amount, String last4, int month, int year) {
        return new AuthorizationRequest(UUID.randomUUID(), new BigDecimal(amount), last4, month, year, "fp");
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("expired card", req("100.00", "1111", 9, 2026), 1L, DeclineReason.CARD_EXPIRED),
                Arguments.of("card ending 0002", req("100.00", "0002", 12, 2028), 1L, DeclineReason.INSUFFICIENT_FUNDS),
                Arguments.of("over 50000", req("50000.01", "1111", 12, 2028), 1L, DeclineReason.LIMIT_EXCEEDED),
                Arguments.of("6th payment in 60s", req("100.00", "1111", 12, 2028), 6L, DeclineReason.VELOCITY_CHECK_FAILED),
                Arguments.of("approved", req("2499.00", "1111", 12, 2028), 1L, null),
                Arguments.of("exactly 50000 is allowed", req("50000.00", "1111", 12, 2028), 1L, null),
                Arguments.of("5th payment in 60s is allowed", req("1.00", "1111", 12, 2028), 5L, null),
                Arguments.of("expires this month is still valid", req("1.00", "1111", 10, 2026), 1L, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void appliesIssuerRules(String name, AuthorizationRequest request, long velocityCount, DeclineReason expected) {
        AuthorizationResult result = service(velocityCount).authorize(request);

        if (expected == null) {
            assertThat(result.approved()).isTrue();
            assertThat(result.authCode()).isEqualTo("A1B2C3");
            assertThat(result.declineReason()).isNull();
        } else {
            assertThat(result.approved()).isFalse();
            assertThat(result.declineReason()).isEqualTo(expected);
            assertThat(result.authCode()).isNull();
        }
    }

    @Test
    void firstDecliningRuleWinsAndLaterRulesAreNotEvaluated() {
        AtomicLong velocityCalls = new AtomicLong();
        VelocityCounter counter = fp -> velocityCalls.incrementAndGet();
        var svc = new AuthorizationService(List.of(new AmountLimitRule(new BigDecimal("10")),
                new VelocityRule(counter, 5)), () -> "X");

        var result = svc.authorize(req("11.00", "1111", 12, 2028));

        assertThat(result.declineReason()).isEqualTo(DeclineReason.LIMIT_EXCEEDED);
        assertThat(velocityCalls.get()).isZero();
    }

    @Test
    void authCodesAreSixUppercaseAlphanumerics() {
        var gen = new AuthCodeGenerator();
        for (int i = 0; i < 50; i++) {
            assertThat(gen.get()).matches("[A-Z0-9]{6}");
        }
    }
}
