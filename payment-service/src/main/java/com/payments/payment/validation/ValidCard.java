package com.payments.payment.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Card number must be 12-19 digits and pass the Luhn checksum. */
@Documented
@Constraint(validatedBy = LuhnValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCard {

    String message() default "cardNumber is not a valid card number";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
