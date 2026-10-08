package com.payments.payment.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Class-level check: expiryMonth/expiryYear must not be in the past. Reported on the "expiryYear" field. */
@Documented
@Constraint(validatedBy = NotExpiredValidator.class)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotExpired {

    String message() default "card is expired";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
