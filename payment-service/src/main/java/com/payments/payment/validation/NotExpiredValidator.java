package com.payments.payment.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Clock;
import java.time.YearMonth;

public class NotExpiredValidator implements ConstraintValidator<NotExpired, HasExpiry> {

    private final Clock clock;

    public NotExpiredValidator() {
        this(Clock.systemUTC());
    }

    NotExpiredValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean isValid(HasExpiry value, ConstraintValidatorContext context) {
        if (value == null || value.expiryMonth() == null || value.expiryYear() == null
                || value.expiryMonth() < 1 || value.expiryMonth() > 12) {
            return true; // @NotNull / @Min / @Max report these
        }
        YearMonth expiry = YearMonth.of(value.expiryYear(), value.expiryMonth());
        if (!expiry.isBefore(YearMonth.now(clock))) {
            return true;
        }
        if (context != null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("expiryYear")
                    .addConstraintViolation();
        }
        return false;
    }
}
