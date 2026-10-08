package com.payments.payment.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotExpiredValidatorTest {

    private final NotExpiredValidator validator =
            new NotExpiredValidator(Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC));

    record Expiry(Integer expiryMonth, Integer expiryYear) implements HasExpiry { }

    @Test
    void futureAndCurrentMonthAreValid() {
        assertThat(validator.isValid(new Expiry(12, 2028), null)).isTrue();
        assertThat(validator.isValid(new Expiry(10, 2026), null)).isTrue();
    }

    @Test
    void pastMonthIsExpiredAndReportedOnExpiryYear() {
        ConstraintValidatorContext ctx = mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);

        assertThat(validator.isValid(new Expiry(9, 2026), ctx)).isFalse();
        verify(ctx).disableDefaultConstraintViolation();
    }

    @Test
    void incompleteValuesAreLeftToOtherConstraints() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid(new Expiry(null, 2020), null)).isTrue();
        assertThat(validator.isValid(new Expiry(1, null), null)).isTrue();
        assertThat(validator.isValid(new Expiry(13, 2020), null)).isTrue();
        assertThat(validator.isValid(new Expiry(0, 2020), null)).isTrue();
    }

    @Test
    void defaultConstructorUsesSystemClock() {
        assertThat(new NotExpiredValidator().isValid(new Expiry(12, 2099), null)).isTrue();
    }
}
